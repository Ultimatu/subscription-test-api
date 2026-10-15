# Décisions d'architecture (ADR)

Chaque décision structurante du projet est consignée dans une fiche *Architecture Decision Record* : le contexte qui l'a motivée, la décision prise, les alternatives écartées et ses conséquences, y compris négatives. Les fiches sont rédigées au moment où la décision est prise.

Une décision n'est jamais réécrite après coup : si elle change, une nouvelle fiche la remplace et l'ancienne passe au statut « Remplacée par ADR-XXXX ».

| N° | Décision | Statut |
|---|---|---|
| [0001](0001-architecture-hexagonale.md) | Architecture hexagonale, appliquée de façon pragmatique | Acceptée |
| [0002](0002-schema-versionne-flyway.md) | Schéma versionné avec Flyway, règles métier doublées en base | Acceptée |
| [0003](0003-configuration.md) | Configuration par `application.yml` et variables d'environnement, sans `.env` | Acceptée |
| [0004](0004-audit-et-historique.md) | Audit des modifications et historique des statuts | Acceptée |
| [0005](0005-suppression-logique.md) | Suppression logique des clients et des souscriptions | Acceptée |
| [0006](0006-technologies-donnees-de-reference.md) | Les technologies sont des données de référence, pas un enum | Acceptée |
| [0007](0007-format-des-erreurs.md) | Erreurs au format Problem Details avec un code stable | Acceptée |
| [0008](0008-tracabilite-des-requetes.md) | Traçabilité des requêtes par `X-Request-Id` | Acceptée |
| [0009](0009-prefixe-api.md) | Préfixe `/api` centralisé, sans version dans l'URL | Acceptée |
| [0010](0010-strategie-de-tests.md) | Stratégie de tests | Acceptée |
| [0011](0011-montants.md) | Représentation des montants | Acceptée |
| [0012](0012-constructeurs-explicites.md) | Constructeurs d'injection écrits explicitement, sans Lombok | Acceptée |
| [0013](0013-securite.md) | Authentification JWT avec les rôles ADMIN et CONSEILLER | Acceptée |
| [0014](0014-souscriptions-numerotation-et-concurrence.md) | Souscriptions : numérotation et accès concurrents | Acceptée |
| [0015](0015-modele-de-lecture-des-souscriptions.md) | Modèle de lecture distinct pour les souscriptions | Acceptée |
| [0016](0016-couverture-de-code.md) | Mesure et seuil de couverture de code | Acceptée |
| [0017](0017-recherche-insensible-aux-accents.md) | Recherche insensible aux accents avec l'extension unaccent | Acceptée |
| [0018](0018-prix-fige-a-la-souscription.md) | Prix mensuel figé à la souscription | Acceptée |
| [0019](0019-attestation-pdf.md) | Attestation de souscription au format PDF | Acceptée |

Les interprétations des points ambigus de l'énoncé sont regroupées dans [../interpretations.md](../interpretations.md).
Le schéma de la base (tables, contraintes, index et requêtes servies) est décrit dans [../base-de-donnees.md](../base-de-donnees.md).
