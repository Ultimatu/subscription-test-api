# ADR-0018 — Prix mensuel figé à la souscription

- **Statut** : Acceptée et mise en œuvre (15/10/2026)

## Contexte

Le prix d'une souscription était lu sur son offre. Or le prix d'une offre est modifiable (règle 10) : une hausse du catalogue changeait rétroactivement le revenu des souscriptions existantes, et une attestation de souscription aurait affiché un prix que le client n'a jamais souscrit.

Cette limite figurait dans la section « Limites et améliorations » du README. Le délai laissé pour le test étant largement suffisant, elle a été levée : c'est une **extension au-delà de l'énoncé**, qui ne modifie pas son contrat d'API.

## Décision

- La souscription conserve son **prix mensuel**, copié depuis l'offre à la création et jamais modifié ensuite. Dans le domaine, `Subscription.create` reçoit l'offre elle-même, pour que le prix ne puisse venir que d'elle ; en base, la colonne `subscription.monthly_price` n'est pas modifiable par l'application (`updatable = false`).
- Les **revenus** (synthèse d'un client, statistiques par ville) sont calculés sur ce prix figé.
- La réponse d'une souscription expose `monthlyPrice`, le prix souscrit, en plus de `offer.monthlyPrice`, le prix actuel du catalogue. C'est un champ ajouté : aucun champ existant ne change.
- Migration V9 : les souscriptions déjà enregistrées reprennent le prix actuel de leur offre, seule information disponible ; une contrainte `ck_subscription_monthly_price` impose un prix strictement positif, comme pour l'offre (règle 8).

## Alternatives envisagées

- **Historiser les prix des offres** (une table des prix avec leur période de validité) et retrouver le prix en vigueur à la date de souscription : plus général, puisqu'il garde aussi l'évolution du catalogue, mais plus complexe pour un seul besoin.
- **Garder le prix actuel** : simple, mais faux dès qu'il s'agit de facturer ou d'attester.

## Conséquences

- Une hausse de prix du catalogue ne s'applique qu'aux nouvelles souscriptions. L'appliquer aux souscriptions existantes serait une révision tarifaire, à concevoir comme une opération explicite (avec information du client).
- Les statistiques de revenu reflètent ce que les clients ont réellement souscrit.
- L'attestation de souscription peut afficher le prix souscrit sans risque d'erreur.
