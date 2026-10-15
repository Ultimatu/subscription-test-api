# ADR-0025 — Accès concurrents : verrou du client avant toute lecture, portée du verrou optimiste

- **Statut** : Acceptée et mise en œuvre (15/10/2026). Complète l'[ADR-0014](0014-souscriptions-numerotation-et-concurrence.md) et précise l'[ADR-0004](0004-audit-et-historique.md).

## Contexte

L'[ADR-0014](0014-souscriptions-numerotation-et-concurrence.md) verrouille la ligne du client (`SELECT … FOR UPDATE`) pendant la création d'une souscription et ses changements de statut. Une relecture du code a relevé trois défauts :

- **Un changement de statut lisait la souscription avant de verrouiller le client.** Deux résiliations simultanées lisaient toutes deux une souscription ACTIVE ; la seconde attendait le verrou, puis appliquait la résiliation à un état périmé. Le verrou optimiste (`@Version`) détectait le conflit à l'enregistrement : 409 `CONCURRENT_MODIFICATION` (« veuillez réessayer »), alors que la réponse attendue est celle d'une seconde résiliation faite après la première, 409 `INVALID_STATUS_TRANSITION`. La suppression d'une souscription avait le même défaut.
- **La suppression et la purge d'un client ne prenaient pas le verrou.** Une souscription créée au même moment échappait à la vérification de la règle 6 : elle survivait à son client supprimé logiquement, ou faisait échouer sa suppression définitive sur la clé étrangère, avec une erreur 500 au lieu d'un refus métier.
- **Aucun test ne prouvait le comportement concurrent.** Deux threads lancés « en même temps » ne se croisent pas forcément : un tel test passerait aussi bien sans verrou.

Enfin, l'[ADR-0004](0004-audit-et-historique.md) présentait le verrou optimiste comme une protection contre l'écrasement silencieux de deux modifications simultanées. Ce n'est vrai que pour deux transactions qui se chevauchent ; la version n'étant pas exposée par l'API, elle ne protège pas une modification fondée sur une lecture ancienne.

## Décision

**Une seule règle de verrouillage.** Toute écriture qui touche aux souscriptions d'un client verrouille **d'abord** la ligne du client, **avant de lire** ce qu'elle va vérifier :

| Opération | Vérification protégée |
|---|---|
| Création d'une souscription | Règles 12 à 14 (client actif, aucune souscription suspendue, unicité par technologie) |
| Changement de statut, suppression d'une souscription | Transition autorisée depuis le statut **actuel** ; seule une souscription PENDING se supprime (règle 22) |
| Suppression, purge d'un client encore visible | Aucune souscription en cours (règle 6) |

- Pour une souscription, le service lit d'abord l'identifiant de son client, par une requête qui ne charge pas la souscription, verrouille le client, puis lit la souscription. Elle est alors dans son dernier état validé : la seconde de deux requêtes simultanées voit le résultat de la première, et le domaine lui répond **comme si elles s'étaient suivies**.
- Chaque opération ne prend qu'un verrou, toujours le même : deux de ces opérations ne peuvent pas s'interbloquer.
- **Test** : `SubscriptionConcurrencyTest` envoie deux requêtes HTTP en parallèle à l'application démarrée sur un port aléatoire. Pour qu'elles se croisent réellement, le test tient lui-même le verrou du client, attend (via `pg_stat_activity`) que les deux requêtes soient bloquées dessus, puis le relâche. Deux créations FTTH donnent un 201 et un 409 `TECHNOLOGY_ALREADY_SUBSCRIBED` ; deux résiliations donnent un 200 et un 409 `INVALID_STATUS_TRANSITION`, avec un seul passage à TERMINATED dans l'historique ; une création suivie d'une suppression du client donnent un 201 et un 409 `CLIENT_HAS_ONGOING_SUBSCRIPTIONS`. Avec l'ancien code, les deux derniers tests échouent, sur `CONCURRENT_MODIFICATION` et sur une erreur 500.

**Portée du verrou optimiste.** La colonne `version` reste en place comme filet de sécurité : si deux transactions modifient la même ligne en même temps sans passer par le verrou du client (modification d'un client ou d'une offre, par exemple), la seconde échoue (409 `CONCURRENT_MODIFICATION`) au lieu d'écraser la première. En revanche, deux conseillers qui consultent le même client puis l'enregistrent l'un après l'autre (`GET`, puis `PUT`) ne sont pas protégés : chaque `PUT` relit la version courante, et le second écrase la modification du premier sans le savoir. C'est une **limite connue**, mentionnée dans le README.

## Alternatives envisagées

- **Verrouiller la ligne de la souscription, puis celle du client** : corrige les changements de statut, mais la suppression d'un client verrouille le client puis modifie ses souscriptions. Deux opérations prendraient alors les mêmes verrous dans un ordre différent, avec un risque d'interblocage. Un verrou unique, pris en premier, est une règle plus simple à appliquer et à vérifier.
- **Relire la souscription après le verrou** (`refresh`) : c'est une opération JPA, qu'un port de sortie ne devrait pas exposer au service.
- **Isolation `SERIALIZABLE`** : PostgreSQL détecterait toutes les anomalies, mais en annulant des transactions (erreur 40001) qu'il faudrait rejouer partout, avec des messages d'erreur moins explicites.
- **Exposer la version** (en-tête `ETag` sur les lectures, `If-Match` sur les `PUT`, 412 si la ressource a changé) : c'est la réponse au problème de la lecture ancienne. Facultatif, l'en-tête ne modifierait pas le contrat de l'énoncé ; il demande cependant de faire circuler la version jusqu'au domaine et de l'utiliser dans les clients de l'API. Laissé en amélioration.

## Conséquences

- Les requêtes simultanées sur un même client reçoivent les mêmes erreurs métier que des requêtes successives ; `CONCURRENT_MODIFICATION` ne sort plus que pour les ressources hors de ce verrou.
- Les écritures sur les souscriptions d'un même client s'exécutent l'une après l'autre, ce qui est sans conséquence pour un outil de back-office ; les autres clients ne sont pas ralentis.
- Un changement de statut coûte une requête de plus (l'identifiant du client), sur la clé primaire.
- Le test de concurrence valide réellement ses données et efface ensuite ce qu'il a créé.
