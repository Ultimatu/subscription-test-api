# ADR-0010 — Stratégie de tests

- **Statut** : Acceptée (14/10/2026). Classement des tests par étiquette ajouté le 15/10/2026 (ancienne ADR-0026).

## Contexte

L'énoncé exige au moins 60 % de couverture par des **tests unitaires**. Au-delà du chiffre, les 22 règles métier doivent être prouvées, et les tests doivent pouvoir tourner chez le jury avec, pour seul prérequis, la base PostgreSQL déjà demandée pour l'application.

`mvn test` exécute aussi des tests d'intégration sur PostgreSQL : pour mesurer les seuls tests unitaires, il faut savoir les distinguer sans liste tenue à la main, où un test oublié fausserait le chiffre publié sans aucune alerte.

## Décision

Chaque couche est testée au niveau qui lui correspond :

| Couche | Type de test | Outils |
|---|---|---|
| Domaine | unitaire pur, sans Spring | JUnit 5, AssertJ |
| Services (`application`) | unitaire, ports simulés | Mockito |
| Persistance | intégration sur le vrai PostgreSQL, annulée en fin de test | `@PersistenceAdapterTest` (slice `@DataJpaTest`) |
| Web | slice MVC, cas d'usage simulés | `@WebAdapterTest` (slice `@WebMvcTest`), `@MockitoBean` |
| Architecture | règles de dépendance | ArchUnit |
| Application complète | sécurité, documentation OpenAPI, accès concurrents, notifications | `@IntegrationTest` (`@SpringBootTest`) |

- Le tableau des transitions de l'énoncé est **réécrit indépendamment dans le test** et confronté au code sur les 16 combinaisons statut × opération, pour éviter un test qui vérifierait le code avec lui-même.
- Les tests tournent dans un **schéma PostgreSQL dédié (`test`)** de la même base, que Flyway crée et migre automatiquement : les données saisies via l'application ne perturbent jamais les tests, et aucune seconde base n'est à créer.
- La date du jour est fournie par un `Clock` injecté, figé dans les tests qui en dépendent.

**Classement des tests**

- Un test d'intégration est un test qui **démarre la base de données**. Il porte l'étiquette JUnit 5 **`integration`**, posée par les méta-annotations `@IntegrationTest` et `@PersistenceAdapterTest`. Les tests de controllers (`@WebAdapterTest`) restent unitaires : ils ne démarrent que la couche web.
- `mvn test` exécute tous les tests ; `mvn test -DexcludedGroups=integration` n'exécute que les tests unitaires, et `mvn test -Dgroups=integration` que les tests d'intégration.
- **Une règle ArchUnit** (`TestClassificationTest`) fait échouer le build si une classe de test démarre la base (`@SpringBootTest` ou `@DataJpaTest`, directement ou par une méta-annotation) sans porter l'étiquette.
- Les tests suivent la convention Maven : chaque test se trouve dans le package de la classe qu'il teste, sans dossiers `unit/` ou `integration/`. Un **test de régression** n'est pas une catégorie à part : c'est un test écrit avec le correctif d'un défaut, qui échoue sans ce correctif, rangé avec les autres tests de la classe concernée.

## Alternatives envisagées

- **H2 en mémoire** : ne reproduit pas fidèlement PostgreSQL (index uniques partiels, contraintes par expression régulière). Les tests passeraient sur H2 et pourraient échouer en réel.
- **Testcontainers** : la solution idéale pour des tests d'intégration isolés, mais elle exige Docker, ce que l'énoncé ne prévoit pas dans les prérequis.
- **Surefire et Failsafe**, avec les tests d'intégration renommés en `*IT` : la séparation classique de Maven, mais elle change le sens de `mvn test`, que l'énoncé emploie pour exécuter tous les tests, et oblige à fusionner deux rapports de couverture.
- **Une liste de classes à exclure de la mesure**, ou **des dossiers par type de test** : rien ne signale un oubli, et le classement reposerait sur le rangement plutôt que sur ce que fait le test.

## Conséquences

- Les règles métier se testent en quelques millisecondes ; les tests d'intégration couvrent ce que seul PostgreSQL peut garantir.
- `mvn test` nécessite la base PostgreSQL locale, comme l'application elle-même.
- Un nouveau test d'intégration doit employer `@IntegrationTest` ou `@PersistenceAdapterTest`, jamais `@SpringBootTest` ou `@DataJpaTest` directement : un test mal classé fait échouer le build.
