# ADR-0014 — Souscriptions : numérotation et accès concurrents

- **Statut** : Proposée (14/10/2026)

## Contexte

- Le serveur attribue à chaque souscription une **référence** au format `SUB-{ANNÉE}-{6 chiffres}` (règle 16) et un **ND** au format `27XXXXXXXX` (règle 17), tous deux uniques.
- Les règles 13 et 14 s'appuient sur les autres souscriptions du client. Deux requêtes simultanées pour le même client peuvent toutes deux constater qu'il n'a pas encore de souscription FTTH en cours, et en créer deux.

## Décision

**Numérotation**

- **ND** : une séquence PostgreSQL, préfixée par `27` et complétée à 8 chiffres (`2700000001`, `2700000002`…). Elle est unique par construction et sans conflit possible entre requêtes simultanées. Sa capacité est de 100 millions de numéros.
- **Référence** : un compteur par année, incrémenté atomiquement en une requête (`insert … on conflict do update … returning`). La numérotation repart de `000001` chaque année, l'année étant celle de la création.
- Les numéros ne sont tirés **qu'une fois toutes les règles vérifiées**, pour ne pas en consommer sur des demandes refusées.
- La génération passe par un port de sortie (`SubscriptionNumberGenerator`), implémenté par l'adaptateur de persistance.

**Accès concurrents**

- La création d'une souscription et les changements de statut **verrouillent la ligne du client** (`SELECT … FOR UPDATE`) : les opérations portant sur un même client s'exécutent l'une après l'autre, sans ralentir les autres clients.
- En complément, la base garantit la règle 14. La technologie de l'offre est **copiée dans la souscription**, ce qui est sans risque puisqu'elle n'est jamais modifiable (règle 10). Un **index unique partiel** interdit alors deux souscriptions en cours (PENDING ou ACTIVE) de la même technologie pour un même client.

**Ordre des vérifications à la création**

Les données invalides sont signalées avant les conflits, puis l'ordre imposé par la règle 15 s'applique :

1. date de début dans le passé : 400 `START_DATE_IN_PAST` ;
2. client ou offre introuvable : 404 ;
3. client non actif : 409 `CLIENT_INACTIVE` ;
4. offre inactive : 409 `OFFER_INACTIVE` ;
5. une souscription SUSPENDED existe : 409 `CLIENT_HAS_SUSPENDED_SUBSCRIPTION` ;
6. une souscription en cours de la même technologie existe : 409 `TECHNOLOGY_ALREADY_SUBSCRIBED`.

## Alternatives envisagées

- **ND tiré au hasard**, avec un nouvel essai en cas de collision : numéros imprévisibles, mais une boucle de nouvelles tentatives à gérer. Un numéro de ligne n'a pas besoin d'être secret.
- **Référence fondée sur une séquence unique** : plus simple, mais la numérotation ne repart pas chaque année et sature à 999 999 souscriptions au total.
- **Se reposer sur le seul verrou optimiste** (`@Version`) : il protège une souscription modifiée deux fois en même temps, pas l'ensemble des souscriptions d'un client.

## Conséquences

- Les règles 13 et 14 restent vraies même sous des requêtes simultanées.
- La technologie est stockée deux fois (offre et souscription), ce qui simplifie au passage la recherche et les statistiques par technologie.
- Les ND se suivent et sont donc prévisibles.
