# ADR-0013 — Authentification JWT avec les rôles ADMIN et CONSEILLER

- **Statut** : Acceptée (14/10/2026) — implémentation à venir

## Contexte

L'énoncé ne demande pas d'authentification, mais l'audit ([ADR-0004](0004-audit-et-historique.md)) n'a de sens que si l'on sait qui agit. L'API est un outil de back-office : ce sont des conseillers et des administrateurs qui créent les clients, activent ou résilient les lignes et consultent les statistiques. Les clients finaux ne l'utilisent pas directement.

## Décision

- Authentification par **jeton JWT**, avec le support natif de Spring Security (`spring-boot-starter-oauth2-resource-server`), sans bibliothèque JWT tierce.
- Deux rôles : **ADMIN** (catalogue des offres et des technologies, suppressions, statistiques) et **CONSEILLER** (clients et souscriptions).
- La sécurité est **désactivable par une propriété**, et la collection Postman livrée se connecte et transmet le jeton automatiquement : le contrat d'API de l'énoncé reste testable tel quel.
- Swagger UI et le point de santé restent publics.

En attendant, `SecurityConfig` ouvre toutes les routes et l'auteur enregistré par l'audit vaut `anonymous`.

## Alternatives envisagées

- **Un rôle CLIENT**, qui permettrait aux clients de consulter leurs propres souscriptions : cela supposerait des comptes clients, une gestion des identifiants et un filtrage par propriétaire. C'est une autre application que celle décrite par l'énoncé.
- **Aucune authentification** : conforme à l'énoncé, mais l'audit perdrait son intérêt.

## Conséquences

- Chaque modification sera attribuée à un utilisateur identifié.
- La gestion des utilisateurs reste minimale (utilisateurs de démonstration) ; en production, l'émission des jetons serait déléguée à un fournisseur d'identité.
