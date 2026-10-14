# ADR-0002 — Schéma versionné avec Flyway, règles métier doublées en base

- **Statut** : Acceptée (14/10/2026)

## Contexte

L'application doit démarrer avec un simple `mvn spring-boot:run` sur une base PostgreSQL vide, et le catalogue des 11 offres doit être chargé au démarrage. Le schéma doit être reproductible et son évolution traçable.

## Décision

- Le schéma est décrit par des **migrations SQL versionnées** Flyway (`src/main/resources/db/migration`), appliquées automatiquement au démarrage. Une migration déjà appliquée **n'est jamais modifiée** : tout changement passe par une nouvelle migration.
- Hibernate est en mode **`ddl-auto: validate`** : il vérifie au démarrage que le mapping correspond au schéma, mais ne le modifie jamais.
- Le **catalogue des offres est une migration** (`V2__insert_offer_catalogue.sql`), exécutée une seule fois, avec les libellés sans accents exigés par l'énoncé.
- Les règles qui peuvent l'être sont **aussi garanties par la base** : unicité de l'email, du code d'offre, de la référence et du ND, formats vérifiés par des contraintes `CHECK` (téléphone, ND, référence), prix strictement positif, clés étrangères. Les adaptateurs traduisent ces violations en erreurs métier lorsque deux requêtes simultanées passent la vérification Java.

## Alternatives envisagées

- **`ddl-auto: update`** (l'équivalent de `synchronize: true` en TypeORM) : pratique pour un prototype, mais aucun historique, ne supprime jamais de colonne et gère mal les renommages.
- **Liquibase** : plus riche (formats XML/YAML, retour arrière), mais plus lourd que nécessaire ici.
- **`data.sql` ou un `CommandLineRunner`** pour charger les offres : exécutés à chaque démarrage, ils obligent à gérer soi-même les doublons.

## Conséquences

- Le jury obtient les tables et les 11 offres au premier démarrage, sans aucune étape manuelle.
- Le SQL est écrit à la main, sans génération à partir des entités : c'est plus de travail, mais le schéma est exactement maîtrisé (index, contraintes partielles, types).
- Une règle violée par une requête concurrente est rattrapée par la base au lieu de corrompre les données.
