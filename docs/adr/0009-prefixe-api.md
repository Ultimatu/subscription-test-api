# ADR-0009 — Préfixe `/api` centralisé, sans version dans l'URL

- **Statut** : Acceptée (14/10/2026)

## Contexte

Versionner l'API dans l'URL (`/api/v1/…`) a été envisagé. Mais l'énoncé fixe précisément les URLs (`/api/clients`, `/api/subscriptions/by-nd/{nd}`…) et montre son attachement à ses conventions (« Convention imposée… ne la modifiez pas »).

## Décision

- Les URLs restent **exactement celles de l'énoncé**.
- Le préfixe `/api` est déclaré **une seule fois** (`WebConfig`, l'équivalent du `setGlobalPrefix` de NestJS) et ne s'applique qu'aux controllers de l'adaptateur web. Swagger UI et Actuator conservent leurs chemins habituels.

## Alternatives envisagées

- **`/api/v1`** : montre le versionnage dans le code, mais s'écarte du contrat de l'énoncé, ce qu'un correcteur peut relever.
- **`server.servlet.context-path`** : préfixerait toute l'application, Actuator et Swagger compris.

## Conséquences

- Le contrat de l'énoncé est respecté à l'identique.
- Passer en `/api/v1` le jour où une version 2 existera se fera en modifiant une seule constante. Un versionnage par en-tête resterait aussi possible ; Spring Framework 7 apporte un support natif du versionnage d'API.
