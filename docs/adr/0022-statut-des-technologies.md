# ADR-0022 — Retrait d'une technologie du catalogue

- **Statut** : Acceptée et mise en œuvre (15/10/2026)

## Contexte

Les technologies sont des données de référence ([ADR-0006](0006-technologies-donnees-de-reference.md)) : on peut en ajouter sans modifier le code, mais pas en retirer une. Or un opérateur arrête parfois de commercialiser une technologie entière (une génération de box remplacée par la suivante, par exemple) sans supprimer pour autant les offres et les souscriptions en cours. Supprimer la technologie est impossible : les offres et les souscriptions la référencent.

L'énoncé ne le prévoit pas : c'est une **extension au-delà de l'énoncé**, réalisée parce que le délai le permettait. Elle ajoute des endpoints sans modifier ceux de l'énoncé.

## Décision

- Une technologie porte un indicateur **`active`** (migration V11 ; les technologies existantes restent actives). `PATCH /api/technologies/{code}/deactivate` la retire du catalogue, `PATCH /api/technologies/{code}/activate` l'y remet ; les deux sont réservés au rôle ADMIN et sans effet si la technologie est déjà dans l'état demandé. `GET /api/technologies?active=true|false` filtre le catalogue.
- Une technologie retirée **n'accepte plus de nouvelle offre ni de nouvelle souscription** (409 `TECHNOLOGY_INACTIVE`). Les offres et les souscriptions existantes ne sont pas affectées : elles peuvent toujours changer de statut, exactement comme pour une offre désactivée (règle 11).
- À la souscription, la vérification se place **juste après celle de l'offre active**, dont elle partage la nature (un produit qui n'est plus commercialisé) : client actif, offre active, technologie active, absence de souscription suspendue, unicité par technologie. L'ordre imposé par la règle 15 entre les règles de l'énoncé est conservé.
- La règle est portée par le domaine (`Technology.ensureActive()`), appelée par la création d'une offre et par `SubscriptionEligibility`.

## Alternatives envisagées

- **Désactiver en cascade toutes les offres de la technologie** : aucun nouveau contrôle à la souscription, mais l'opération est irréversible, faute de moyen de réactiver une offre dans l'énoncé ; remettre la technologie au catalogue laisserait ses offres retirées.
- **Ne rien ajouter et désactiver les offres une à une** : possible, mais une offre créée ensuite sur la technologie redeviendrait commercialisable.

## Conséquences

- Retirer une technologie est réversible et n'affecte aucun client existant.
- Les statistiques par technologie continuent d'afficher les technologies retirées, avec leurs souscriptions actives.
