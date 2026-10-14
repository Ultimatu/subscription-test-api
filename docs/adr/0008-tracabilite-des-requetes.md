# ADR-0008 — Traçabilité des requêtes par `X-Request-Id`

- **Statut** : Acceptée (14/10/2026)

## Contexte

Quand un conseiller signale une erreur, il faut retrouver dans les logs toutes les lignes de sa requête, parmi celles de toutes les requêtes traitées en parallèle.

## Décision

Un filtre (`RequestTracingFilter`, exécuté avant tout autre traitement) :

- reprend l'en-tête **`X-Request-Id`** envoyé par l'appelant **s'il est sûr** (lettres, chiffres et tirets, 64 caractères au plus), et en génère un sinon. Une valeur arbitraire contenant un retour à la ligne permettrait d'injecter de fausses lignes dans les logs ;
- le renvoie dans l'en-tête de la réponse et dans le corps des erreurs (`requestId`) ;
- le place dans le contexte de log (MDC) : **chaque ligne de log** de la requête l'affiche ;
- écrit une ligne de log par appel à l'API (méthode, chemin, statut, durée), **sans la query string**, qui peut contenir des données personnelles (un email recherché, par exemple).

## Alternatives envisagées

- **Micrometer Tracing / OpenTelemetry** : la solution de référence pour suivre une requête à travers plusieurs services, surdimensionnée pour un service unique. Le filtre pourra être remplacé le jour où le système deviendra distribué.

## Conséquences

- Un identifiant affiché à l'utilisateur dans une erreur suffit à retrouver tout le parcours de sa requête dans les logs.
- Un front peut fournir son propre identifiant pour relier ses logs à ceux de l'API.
