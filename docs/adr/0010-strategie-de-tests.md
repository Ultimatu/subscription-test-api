# ADR-0010 — Stratégie de tests

- **Statut** : Acceptée (14/10/2026)

## Contexte

L'énoncé exige au moins 60 % de couverture par des tests unitaires. Au-delà du chiffre, les 22 règles métier doivent être prouvées, et les tests doivent pouvoir tourner chez le jury avec, pour seul prérequis, la base PostgreSQL déjà demandée pour l'application.

## Décision

Chaque couche est testée au niveau qui lui correspond :

| Couche | Type de test | Outils |
|---|---|---|
| Domaine | unitaire pur, sans Spring | JUnit 5, AssertJ |
| Services (`application`) | unitaire, ports simulés | Mockito |
| Persistance | intégration sur le vrai PostgreSQL, annulée en fin de test | `@PersistenceAdapterTest` (slice `@DataJpaTest`) |
| Web | slice MVC, cas d'usage simulés | `@WebAdapterTest` (slice `@WebMvcTest`), `@MockitoBean` |
| Architecture | règles de dépendance | ArchUnit |
| Documentation | contenu de la spécification OpenAPI générée | `@SpringBootTest` |

- Le tableau des transitions de l'énoncé est **réécrit indépendamment dans le test** et confronté au code sur les 16 combinaisons statut × opération, pour éviter un test qui vérifierait le code avec lui-même.
- Les tests tournent dans un **schéma PostgreSQL dédié (`test`)** de la même base, que Flyway crée et migre automatiquement : les données saisies via l'application ne perturbent jamais les tests, et aucune seconde base n'est à créer.
- La date du jour est fournie par un `Clock` injecté, figé dans les tests qui en dépendent.

## Alternatives envisagées

- **H2 en mémoire** : ne reproduit pas fidèlement PostgreSQL (index uniques partiels, contraintes par expression régulière). Les tests passeraient sur H2 et pourraient échouer en réel.
- **Testcontainers** : la solution idéale pour des tests d'intégration isolés, mais elle exige Docker, ce que l'énoncé ne prévoit pas dans les prérequis.

## Conséquences

- Les règles métier se testent en quelques millisecondes ; les tests d'intégration couvrent ce que seul PostgreSQL peut garantir.
- `mvn test` nécessite la base PostgreSQL locale, comme l'application elle-même.
