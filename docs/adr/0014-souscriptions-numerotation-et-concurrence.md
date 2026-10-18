# ADR-0014 — Souscriptions : numérotation et accès concurrents

- **Statut** : Acceptée (14/10/2026). Règle de verrouillage revue le 15/10/2026 après une relecture du code, avec un test de concurrence (ancienne ADR-0025).

## Contexte

- Le serveur attribue à chaque souscription une **référence** au format `SUB-{ANNÉE}-{6 chiffres}` (règle 16) et un **ND** au format `27XXXXXXXX` (règle 17), tous deux uniques.
- Plusieurs règles s'appuient sur d'autres lignes que celle modifiée : les règles 13 et 14 sur les autres souscriptions du client, la règle 6 sur ses souscriptions en cours, une transition sur le statut actuel. Deux requêtes simultanées peuvent toutes deux constater qu'un client n'a pas de souscription FTTH en cours, et en créer deux.
- Une première version lisait la souscription **avant** de verrouiller le client, et la suppression d'un client ne verrouillait rien : deux résiliations simultanées recevaient une erreur technique (409 `CONCURRENT_MODIFICATION`) au lieu d'un refus métier, et une souscription créée pendant la suppression de son client lui survivait, ou provoquait une erreur 500.

## Décision

**Numérotation**

- **ND** : une séquence PostgreSQL, préfixée par `27` et complétée à 8 chiffres (`2700000001`, `2700000002`…), unique par construction. Sa capacité est de 100 millions de numéros.
- **Référence** : un compteur par année, incrémenté atomiquement en une requête (`insert … on conflict do update … returning`). La numérotation repart de `000001` chaque année.
- Les numéros ne sont tirés **qu'une fois toutes les règles vérifiées**, pour ne pas en consommer sur des demandes refusées. Leur **format** est une règle métier, porté par le domaine (`SubscriptionNumbering`) ; l'obtention de numéros uniques relève de l'infrastructure (port `SubscriptionNumberSequences`).

**Verrou du client**

Toute écriture qui touche aux souscriptions d'un client verrouille **d'abord** la ligne du client (`SELECT … FOR UPDATE`), **avant de lire** ce qu'elle va vérifier :

| Opération | Vérification protégée |
|---|---|
| Création d'une souscription | Règles 12 à 14 (client actif, aucune souscription suspendue, unicité par technologie) |
| Changement de statut, suppression d'une souscription | Transition autorisée depuis le statut **actuel** ; seule une souscription PENDING se supprime (règle 22) |
| Suppression, purge d'un client | Aucune souscription en cours (règle 6) |

- Pour une souscription, le service lit d'abord l'identifiant de son client (sans charger la souscription), verrouille le client, puis lit la souscription dans son dernier état validé : la seconde de deux requêtes simultanées reçoit **la réponse qu'elle aurait eue si elles s'étaient suivies**.
- Chaque opération ne prend qu'un verrou, toujours le même : deux opérations ne peuvent pas s'interbloquer. Les autres clients ne sont pas ralentis.
- En complément, la base garantit la règle 14 : la technologie de l'offre est **copiée dans la souscription** (sans risque, elle n'est jamais modifiable, règle 10), et un **index unique partiel** interdit deux souscriptions en cours de la même technologie pour un même client.

**Hors du verrou du client**

- Deux créations avec le même email : l'index unique de la base départage, et sa violation est traduite en 409 `CLIENT_EMAIL_ALREADY_USED`.
- Le compteur des références est incrémenté par une seule requête atomique : deux clients qui souscrivent en même temps obtiennent deux numéros consécutifs.
- Deux modifications simultanées d'une même ligne (un client, une offre) : la colonne `version` (verrou optimiste) fait échouer la seconde, 409 `CONCURRENT_MODIFICATION`, au lieu d'écraser la première. Cette protection se limite aux transactions qui se chevauchent : la version n'étant pas exposée par l'API, deux conseillers qui lisent puis enregistrent l'un après l'autre (`GET`, puis `PUT`) ne sont pas protégés. C'est une **limite connue**, mentionnée dans le README.
- Les notifications sont réservées avec `FOR UPDATE SKIP LOCKED` ([ADR-0013](0013-extensions.md#5-notifications-par-email)) : plusieurs instances de l'application ne les envoient pas deux fois.

**Preuve par les tests**

`ConcurrencyTest` envoie de vraies requêtes HTTP simultanées à l'application démarrée sur un port aléatoire. Deux threads lancés « en même temps » ne se croisant pas forcément, le test **tient lui-même le verrou** (ligne du client, ligne du compteur, insertion non validée du même email ou notification réservée), attend via `pg_stat_activity` que chaque requête soit bloquée dessus, puis le relâche. Les tests des deux défauts décrits plus haut échouent avec l'ancien code, et chaque cas hors du verrou du client échoue si l'on retire sa protection.

**Ordre des vérifications à la création**

Les données invalides sont signalées avant les conflits, puis l'ordre imposé par la règle 15 s'applique :

1. date de début dans le passé : 400 `START_DATE_IN_PAST` ;
2. client ou offre introuvable : 404 ;
3. client non actif : 409 `CLIENT_INACTIVE` ;
4. offre inactive : 409 `OFFER_INACTIVE`, puis technologie retirée du catalogue : 409 `TECHNOLOGY_INACTIVE` ([ADR-0013](0013-extensions.md#6-retrait-dune-technologie-du-catalogue)) ;
5. une souscription SUSPENDED existe : 409 `CLIENT_HAS_SUSPENDED_SUBSCRIPTION` ;
6. une souscription en cours de la même technologie existe : 409 `TECHNOLOGY_ALREADY_SUBSCRIBED`.

## Alternatives envisagées

- **ND tiré au hasard**, avec un nouvel essai en cas de collision : numéros imprévisibles, mais une boucle de nouvelles tentatives à gérer. Un numéro de ligne n'a pas besoin d'être secret.
- **Référence fondée sur une séquence unique** : plus simple, mais la numérotation ne repart pas chaque année et sature à 999 999 souscriptions au total.
- **Se reposer sur le seul verrou optimiste** : il protège une ligne modifiée deux fois en même temps, pas l'ensemble des souscriptions d'un client.
- **Verrouiller la souscription, puis le client** : deux opérations prendraient les mêmes verrous dans un ordre différent, avec un risque d'interblocage.
- **Isolation `SERIALIZABLE`** : PostgreSQL détecterait toutes les anomalies, mais en annulant des transactions (erreur 40001) qu'il faudrait rejouer partout, avec des messages d'erreur moins explicites.
- **Exposer la version** (`ETag` sur les lectures, `If-Match` sur les `PUT`, 412 si la ressource a changé) : la réponse au problème de la lecture ancienne, sans modifier le contrat de l'énoncé, mais il faut faire circuler la version jusqu'au domaine et dans les clients de l'API. Laissé en amélioration.

## Conséquences

- Les requêtes simultanées reçoivent les mêmes erreurs métier que des requêtes successives ; `CONCURRENT_MODIFICATION` ne sort plus que pour les ressources hors du verrou du client.
- Un changement de statut coûte une requête de plus (l'identifiant du client), sur la clé primaire.
- La technologie est stockée deux fois (offre et souscription), ce qui simplifie au passage la recherche et les statistiques par technologie.
- Les ND se suivent et sont donc prévisibles.
- Le test de concurrence valide réellement ses données, puis efface ce qu'il a créé.
