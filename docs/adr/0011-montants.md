# ADR-0011 — Représentation des montants

- **Statut** : Acceptée (14/10/2026)

## Contexte

Les prix mensuels et les revenus sont des montants. Les nombres à virgule flottante (`double`) produisent des erreurs d'arrondi incompatibles avec un calcul de revenu.

## Décision

- Les montants sont des **`BigDecimal`** en Java et des colonnes **`numeric(12,2)`** en base.
- Ils ont **toujours deux décimales** : le domaine les normalise (`25000` devient `25000.00`). Une troisième décimale est **refusée** (erreur 400 `INVALID_MONTHLY_PRICE`) plutôt qu'arrondie en silence par la base. Les sommes sont elles aussi exprimées avec deux décimales (`0.00`).

## Alternatives envisagées

- **Des entiers**, puisque le franc CFA n'a pas de centimes : plus simple, mais lie le modèle à une devise ; `numeric(12,2)` reste correct pour toute devise.

## Conséquences

- Les montants sont exacts et toujours présentés au même format dans l'API.
