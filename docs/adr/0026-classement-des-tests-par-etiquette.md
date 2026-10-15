# ADR-0026 — Classement des tests unitaires et d'intégration par étiquette

- **Statut** : Acceptée et mise en œuvre (15/10/2026). Complète l'[ADR-0010](0010-strategie-de-tests.md) et l'[ADR-0016](0016-couverture-de-code.md).

## Contexte

L'énoncé exige 60 % de couverture par des **tests unitaires**, alors que `mvn test` exécute aussi des tests d'intégration sur PostgreSQL ([ADR-0010](0010-strategie-de-tests.md)). Pour mesurer les seuls tests unitaires, l'[ADR-0016](0016-couverture-de-code.md) excluait les tests d'intégration par une liste de noms de classes tenue à la main. Un nouveau test d'intégration oublié dans la liste aurait été compté parmi les tests unitaires, et le chiffre publié dans le README aurait été faux sans aucune alerte.

Les tests suivent la convention Maven : `src/test/java` reproduit les packages de `src/main/java`, et chaque test se trouve dans le package de la classe qu'il teste, ce qui lui donne accès à ce qui n'est visible que du package. Ranger les tests dans des dossiers `unit/` et `integration/` casserait cette correspondance.

## Décision

- Un test d'intégration est un test qui **démarre la base de données**. Il porte l'étiquette JUnit 5 **`integration`**, posée par deux méta-annotations :
  - `@IntegrationTest` : `@SpringBootTest` (application complète), dont il reprend les attributs `properties` et `webEnvironment` ;
  - `@PersistenceAdapterTest` : la tranche JPA sur PostgreSQL, qui existait déjà.
- Les tests de controllers (`@WebAdapterTest`) restent des tests unitaires : ils ne démarrent que la couche web, sans base.
- `mvn test` exécute toujours tous les tests. `mvn test -DexcludedGroups=integration` n'exécute que les tests unitaires, et `mvn test -Dgroups=integration` que les tests d'intégration.
- **Une règle ArchUnit** (`TestClassificationTest`) fait échouer le build si une classe de test démarre la base (`@SpringBootTest` ou `@DataJpaTest`, directement ou par une méta-annotation) sans porter l'étiquette.
- Un **test de régression** n'est pas une catégorie à part : c'est un test, écrit avec le correctif d'un défaut, qui échoue sans ce correctif. Il est rangé avec les autres tests de la classe concernée.

## Alternatives envisagées

- **Liste de noms à exclure** (situation précédente) : rien ne signale un oubli.
- **Surefire et Failsafe**, avec les tests d'intégration renommés en `*IT` : c'est la séparation classique de Maven, où `mvn test` n'exécute que les tests unitaires et `mvn verify` tous les tests. Elle oblige à renommer onze classes et à fusionner deux rapports de couverture, et change le sens de `mvn test`, que l'énoncé et le README emploient pour exécuter tous les tests.
- **Des dossiers par type de test** : contraire à la convention Maven, et le classement reposerait encore sur le rangement plutôt que sur ce que fait le test.

## Conséquences

- La mesure de la couverture des seuls tests unitaires ne demande plus aucune liste à tenir à jour, et un test mal classé fait échouer le build.
- Un nouveau test d'intégration doit employer `@IntegrationTest` ou `@PersistenceAdapterTest` plutôt que `@SpringBootTest` ou `@DataJpaTest` directement.
- Le build n'exige pas de seuil propre aux tests unitaires : il mesure toujours tous les tests ensemble ([ADR-0016](0016-couverture-de-code.md)).
