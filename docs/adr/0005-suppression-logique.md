# ADR-0005 — Suppression logique des clients et des souscriptions

- **Statut** : Acceptée (14/10/2026), complétée par l'[ADR-0023](0023-suppression-et-conservation.md) : suppression définitive, durée de conservation et purge.

## Contexte

La règle 6 autorise la suppression d'un client dont toutes les souscriptions sont résiliées (TERMINATED). Mais ces souscriptions référencent toujours le client, et les effacer ferait disparaître leur historique, à l'encontre de la traçabilité recherchée ([ADR-0004](0004-audit-et-historique.md)).

## Décision

- Supprimer un client ou une souscription **renseigne `deleted_at` et `deleted_by`** au lieu d'effacer la ligne.
- Une annotation Hibernate `@SQLRestriction("deleted_at is null")` rend les lignes supprimées **invisibles dans toutes les requêtes**, y compris les lectures par identifiant (comportement vérifié par un test avec Hibernate 6.6). Il n'y a donc rien à penser au cas par cas.
- Supprimer un client supprime aussi ses souscriptions, nécessairement résiliées à ce stade.
- L'unicité de l'email n'est garantie que parmi les clients existants, grâce à un **index unique partiel** (`where deleted_at is null`) : l'email d'un client supprimé peut être réutilisé par un nouveau client.
- Le ND et la référence restent uniques **pour toujours** : un numéro de ligne n'est jamais réattribué.

## Alternatives envisagées

- **Suppression physique en cascade** : simple, mais l'historique des souscriptions résiliées disparaît.
- **Réserver l'email à vie** : recréer un client supprimé renverrait « email déjà utilisé » pour un client introuvable, ce qui ressemble à un bug.
- **Ressusciter l'ancien client** quand son email revient : mélange deux identités et leurs historiques.

## Conséquences

- Tout reste traçable, et l'API se comporte comme si les données avaient disparu.
- Des données personnelles sont conservées après suppression. En production, il faudrait une durée de conservation suivie d'une anonymisation, conformément à la réglementation ivoirienne sur les données personnelles (ARTCI).
