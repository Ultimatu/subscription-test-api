# ADR-0007 — Erreurs au format Problem Details avec un code stable

- **Statut** : Acceptée (14/10/2026). Traduction des erreurs produites par Spring ajoutée le 18/10/2026.

## Contexte

Les clients de l'API doivent pouvoir distinguer précisément les cas d'erreur (email déjà utilisé, transition interdite…) sans analyser des messages susceptibles de changer, et le support doit pouvoir relier une erreur signalée aux logs du serveur.

## Décision

- Toutes les erreurs suivent le standard **Problem Details (RFC 9457)**, servi en `application/problem+json`, complété de trois champs :
  - **`code`** : identifiant stable en majuscules, par exemple `CLIENT_EMAIL_ALREADY_USED` ;
  - **`timestamp`** ;
  - **`requestId`** : identifiant de la requête, voir [ADR-0008](0008-tracabilite-des-requetes.md).
- Les erreurs de validation ajoutent la liste des champs invalides (`errors`). Les transitions refusées ajoutent le statut courant et les statuts requis, comme l'exige l'énoncé.
- **Le domaine ne connaît pas HTTP.** Chaque exception métier porte un code de l'enum `DomainErrorCode`, et c'est l'adaptateur web (`GlobalExceptionHandler`) qui fait la correspondance avec le statut HTTP :

  | Exception du domaine | Statut HTTP |
  |---|---|
  | `DomainValidationException` | 400 |
  | `ResourceNotFoundException` | 404 |
  | `BusinessRuleViolationException` (dont les transitions refusées) | 409 |

- Les erreurs techniques ont leurs propres codes (`TechnicalErrorCode` : `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `CONCURRENT_MODIFICATION`, `INTERNAL_ERROR`…). Celles que gère Spring lui-même (405, 415…) reçoivent un code tiré de leur statut (`METHOD_NOT_ALLOWED`), et leur titre et leur message sont traduits par `messages.properties`, que `ResponseEntityExceptionHandler` lit par le `MessageSource` (clés `problemDetail.title.<exception>` et `problemDetail.<exception>`).
- Une erreur inattendue renvoie 500 avec un message générique : aucun détail interne ne sort de l'API.
- Les messages sont en français. Un code n'est jamais renommé : on en ajoute un nouveau.
- **Pas d'enveloppe** `{ status, timestamp, data }` sur les réponses de succès.

## Alternatives envisagées

- **Envelopper toutes les réponses** (l'équivalent d'un interceptor de transformation NestJS) : le statut ferait doublon avec le statut HTTP, les erreurs auraient deux formats ou perdraient le standard, la documentation OpenAPI deviendrait fausse et une réponse 204 ne peut pas avoir de corps.
- **Utiliser l'URI `type` de la RFC comme identifiant** : prévu par le standard, mais suppose de publier une page par type d'erreur. Un champ `code` est plus simple à exploiter.

## Conséquences

- Un client de l'API peut traiter chaque cas d'erreur de façon fiable, et le format est documenté dans Swagger (schéma `Problem`).
- Toutes les erreurs que l'API peut rencontrer sont en français, y compris celles de Spring (405, 406, 415, 404 sur une route inconnue, paramètre manquant). Une exception de Spring absente de `messages.properties` garderait son titre anglais : en ajouter une se fait sans code, par deux lignes dans ce fichier.
