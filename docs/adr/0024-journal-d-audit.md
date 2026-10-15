# ADR-0024 — Journal d'audit des actions métier

- **Statut** : Acceptée et mise en œuvre (15/10/2026). Complète l'[ADR-0004](0004-audit-et-historique.md).

## Contexte

L'audit existant ([ADR-0004](0004-audit-et-historique.md)) est un **audit d'état** : chaque ligne porte son créateur et son dernier modificateur, et les souscriptions conservent l'historique de leurs statuts. Il ne permet pas de répondre à « qui a fait quoi, et quand » sur l'ensemble de l'application : une modification efface la trace de la précédente, une suppression ou une purge fait disparaître la ligne, et rien ne garde la trace d'un export de données personnelles.

Le prototype de back-office a dû reconstituer un journal approximatif en croisant les dates de création et de modification de chaque ressource : une quinzaine de requêtes par affichage, limitées aux éléments les plus récents, sans suppressions, purges ni exports. C'est une **extension au-delà de l'énoncé**, réalisée parce que le délai le permettait ; elle ajoute un endpoint sans en modifier aucun.

## Décision

- Une table **`audit_event`** (migration V12), en ajout seul, reçoit un événement par action métier : date, auteur, action, ressource (type et identifiant), résumé lisible, détails en JSON et identifiant de la requête (pour retrouver ses lignes de log, [ADR-0008](0008-tracabilite-des-requetes.md)).
- Les **services applicatifs** consignent chaque action par le port de sortie `AuditTrail`, **dans la transaction de l'action** : une action annulée ne laisse pas de trace, une action validée en laisse toujours une. L'auteur et l'identifiant de la requête sont renseignés par l'adaptateur ; une tâche planifiée signe « system ».
- **Actions consignées** : création, modification (champs modifiés, avec leur valeur avant et après), suppression et purge d'un client ; création, modification et retrait d'une offre ; ajout, retrait et remise au catalogue d'une technologie ; création, chaque changement de statut (avec son motif) et suppression d'une souscription ; **exports** CSV (nombre de lignes et noms des filtres, sans leurs valeurs). Une action sans effet (modification identique, retrait d'une offre déjà retirée) n'est pas consignée.
- **Lecture** : `GET /api/audit-events`, réservé au rôle ADMIN, paginé, du plus récent au plus ancien, filtrable par type et identifiant de ressource, auteur, action et période (jours entiers, heure d'Abidjan).
- **Données personnelles** : les événements d'un client (y compris ceux de ses souscriptions) portent son identifiant. Sa suppression définitive ou sa purge ([ADR-0023](0023-suppression-et-conservation.md)) **efface ces événements** et laisse une seule trace sans donnée personnelle (« Client n° 42 purgé ») : la loi n° 2013-450 interdit tout traitement des données une fois l'effacement effectué (article 36).
- Pas de clé étrangère : le journal doit survivre à la suppression des ressources qu'il décrit.

## Alternatives envisagées

- **Hibernate Envers** : historise automatiquement chaque version de chaque entité dans des tables `_AUD`. C'est un historique technique de lignes, pas d'actions métier : une suspension pour impayé y devient une colonne `status` modifiée. Il ne voit pas les exports et conserverait les données d'un client purgé.
- **Intercepteur HTTP** (l'équivalent d'un intercepteur NestJS) : il connaît l'URL et le statut de la réponse, mais ni le sens métier de l'action ni les valeurs avant et après ; il consignerait aussi les tentatives refusées.
- **Écriture dans les logs** : les logs ne sont ni interrogeables par l'application ni conservés selon les mêmes règles ; ils restent complémentaires, reliés au journal par l'identifiant de la requête.

## Conséquences

- Une seule requête répond à « qui a fait quoi, et quand » ; l'historique des statuts reste la vue détaillée d'une souscription.
- Chaque nouveau cas d'usage doit consigner son action : c'est une ligne de code, vérifiée par les tests des services.
- Le journal grossit sans limite : une politique de conservation propre au journal (archivage, purge des événements anciens) serait à définir en production. Les connexions ne sont pas consignées ; elles pourraient l'être pour la sécurité, au prix d'un volume important.
