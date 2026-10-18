# ADR-0004 — Audit des modifications et historique des statuts

- **Statut** : Acceptée (14/10/2026). Le journal des actions métier, ajouté au-delà de l'énoncé, est décrit dans l'[ADR-0013](0013-extensions.md#8-journal-daudit).

## Contexte

Dans un outil de back-office, il faut pouvoir répondre à « qui a fait quoi, et quand ». La règle 20 exige en plus que le motif d'une suspension ou d'une résiliation soit conservé en base. Or une souscription peut être suspendue, réactivée puis suspendue à nouveau : une simple colonne `reason` écraserait le premier motif.

## Décision

- Toutes les entités portent **`created_at`, `created_by`, `updated_at`, `updated_by`**, renseignés automatiquement par l'audit de Spring Data. L'auteur est le titulaire du jeton ([ADR-0013](0013-extensions.md#1-authentification-jwt-et-rôles)), `anonymous` si la sécurité est désactivée, et `system` pour les données chargées par migration.
- Toutes les entités portent une colonne **`version`** (verrou optimiste), dont la portée est précisée par l'[ADR-0014](0014-souscriptions-numerotation-et-concurrence.md).
- Chaque changement de statut d'une souscription est inséré dans la table **`subscription_status_history`** (ancien statut, nouveau statut, motif, auteur, date). Cette table n'est jamais modifiée, seulement complétée. Le domaine enregistre les changements (`recordedStatusChanges()`), l'adaptateur de persistance les insère.

## Alternatives envisagées

- **Une colonne `reason` sur la souscription** : perd les motifs précédents dès le deuxième cycle de suspension.
- **Hibernate Envers** (versionnement complet de chaque entité) : plus puissant, mais volumineux et moins lisible qu'un historique métier ciblé.

## Conséquences

- L'historique complet d'une souscription est disponible, avec tous ses motifs.
- Les tables grossissent avec l'historique ; une politique d'archivage serait à prévoir en production.
