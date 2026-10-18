# Décisions d'architecture (ADR)

Chaque décision structurante du projet est consignée dans une fiche *Architecture Decision Record* : le contexte qui l'a motivée, la décision prise, les alternatives écartées et ses conséquences, y compris négatives. Une fiche décrit la décision **dans son état actuel** : quand une décision évolue, sa fiche est mise à jour et son statut le signale ; l'historique reste dans Git.

| N° | Décision |
|---|---|
| [0001](0001-architecture-hexagonale.md) | Architecture hexagonale, appliquée de façon pragmatique |
| [0002](0002-schema-versionne-flyway.md) | Schéma versionné avec Flyway, règles métier doublées en base |
| [0004](0004-audit-et-historique.md) | Audit des modifications et historique des statuts |
| [0005](0005-suppression-logique.md) | Suppression logique des clients et des souscriptions |
| [0006](0006-technologies-donnees-de-reference.md) | Les technologies sont des données de référence, pas un enum |
| [0007](0007-format-des-erreurs.md) | Erreurs au format Problem Details avec un code stable |
| [0008](0008-tracabilite-des-requetes.md) | Traçabilité des requêtes par `X-Request-Id` |
| [0010](0010-strategie-de-tests.md) | Stratégie de tests |
| [0013](0013-extensions.md) | **Extensions au-delà de l'énoncé** : authentification et rôles, prix figé, attestation PDF, exports CSV, notifications par email, retrait d'une technologie, suppression définitive et conservation, journal d'audit |
| [0014](0014-souscriptions-numerotation-et-concurrence.md) | Souscriptions : numérotation et accès concurrents |
| [0015](0015-modele-de-lecture-des-souscriptions.md) | Modèle de lecture distinct pour les souscriptions |
| [0016](0016-couverture-de-code.md) | Mesure et seuil de couverture de code |
| [0017](0017-recherche-insensible-aux-accents.md) | Recherche insensible aux accents avec l'extension unaccent |

**Numéros absents.** Les extensions, d'abord décrites chacune dans sa fiche (0018 à 0024), sont regroupées dans l'ADR-0013. Les fiches 0025 et 0026 complétaient une décision antérieure et y ont été fusionnées (0025 → 0014, 0026 → 0010). Les fiches 0003 (configuration), 0009 (préfixe `/api`), 0011 (montants) et 0012 (constructeurs d'injection) tenaient en quelques lignes et sont intégrées au README. Les migrations V9 à V12, qu'on ne modifie jamais une fois appliquées, citent encore les numéros 0018, 0021, 0022 et 0024 : ils renvoient tous à l'ADR-0013.

Les interprétations des points ambigus de l'énoncé sont regroupées dans [../interpretations.md](../interpretations.md).
Le schéma de la base (tables, contraintes, index et requêtes servies) est décrit dans [../base-de-donnees.md](../base-de-donnees.md).
