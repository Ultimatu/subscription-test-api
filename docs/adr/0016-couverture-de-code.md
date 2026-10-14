# ADR-0016 — Mesure et seuil de couverture de code

- **Statut** : Acceptée (14/10/2026)

## Contexte

L'énoncé exige « 60 % de couverture de tests unitaires ». Les tests du projet ([ADR-0010](0010-strategie-de-tests.md)) mêlent des tests unitaires (domaine, services, controllers en slice) et des tests d'intégration sur PostgreSQL (persistance, contexte complet), qui s'exécutent tous avec `mvn test`.

## Décision

- **JaCoCo** mesure la couverture à chaque exécution des tests : `mvn test` produit le rapport `target/site/jacoco/index.html`. Seule la classe de démarrage `SubscriptionApiApplication` est exclue de la mesure.
- La phase **`verify`** fait échouer le build sous **90 % des lignes** ou **75 % des branches**, pour l'ensemble des tests. Ce seuil, bien supérieur aux 60 % de l'énoncé, est placé juste sous le niveau atteint, afin qu'aucune régression ne passe inaperçue. La vérification n'a pas lieu pendant `mvn test`, pour qu'exécuter un seul test en développement n'échoue pas pour couverture insuffisante.
- Mesure relevée le 14/10/2026 :

  | Périmètre | Lignes | Branches |
  |---|---|---|
  | Tous les tests (268) | 98,9 % | 81,0 % |
  | Tests unitaires seuls (225, sans base de données) | 69,5 % | 51,2 % |

  L'exigence de l'énoncé est donc respectée même au sens strict des seuls tests unitaires. La persistance n'est couverte que par les tests d'intégration, ce qui est voulu : la tester sans PostgreSQL n'aurait pas de sens.

## Alternatives envisagées

- **Séparer tests unitaires et tests d'intégration** (Surefire / Failsafe, tests `*IT`) et vérifier la couverture des seuls tests unitaires : plus fidèle à la lettre de l'énoncé, mais `mvn test`, la commande citée par l'énoncé, n'exécuterait plus les tests d'intégration.
- **Seuil à 60 %** : conforme à l'énoncé, mais sans effet tant que la couverture réelle est proche de 99 %.

## Conséquences

- La couverture fait partie des critères de réussite du build, et non d'un simple rapport.
- Tout nouveau code sans test fait baisser la couverture et peut faire échouer `mvn verify` : c'est voulu.
- Pour remesurer les seuls tests unitaires : `mvn test -Dtest='!**/persistence/**/*Test,!SubscriptionApiApplicationTests,!OpenApiDocumentationTest' -Dsurefire.failIfNoSpecifiedTests=false`, après avoir supprimé `target/jacoco.exec`, car JaCoCo cumule les mesures d'une exécution à l'autre.
