# ADR-0003 — Configuration par `application.yml` et variables d'environnement, sans `.env`

- **Statut** : Acceptée (14/10/2026)

## Contexte

L'énoncé demande une application exécutable avec un simple `mvn spring-boot:run` une fois PostgreSQL démarré, et une documentation de la configuration de la base dans `application.yml`. Un fichier `.env`, réflexe courant dans l'écosystème Node.js, a d'abord été envisagé.

## Décision

- La configuration du développement local est écrite dans `application.yml`, identifiants de la base compris (base `subscription_db`, utilisateur `subscription_user`).
- Tout autre environnement surcharge ces valeurs par les **variables d'environnement** que Spring Boot associe automatiquement aux propriétés (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_PASSWORD`…), ou par des arguments `--spring.datasource.password=…`, sans modifier le code.
- Pas de fichier `.env` ni de Docker obligatoire.

## Alternatives envisagées

- **`.env` importé par `spring.config.import`** : techniquement possible, mais ce n'est pas la convention Spring, et le correcteur devrait d'abord copier un `.env.example`, ce qui contredit le « simple `mvn spring-boot:run` ».
- **Profils Spring** (`application-prod.yml`) : réservés au jour où plusieurs environnements existeront vraiment.

## Conséquences

- Le projet démarre sans aucune préparation au-delà de la création de la base.
- Le mot de passe de la base locale figure en clair dans le dépôt : c'est acceptable pour une base de développement, jamais pour un environnement réel, où il viendrait d'une variable d'environnement ou d'un coffre de secrets.
