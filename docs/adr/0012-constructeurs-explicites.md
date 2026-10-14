# ADR-0012 — Constructeurs d'injection écrits explicitement, sans Lombok

- **Statut** : Acceptée (14/10/2026)

## Contexte

Les services, adaptateurs et controllers utilisaient `@RequiredArgsConstructor` de Lombok pour générer leur constructeur d'injection. Le compilateur Eclipse 4.42, embarqué dans les extensions Java récentes de VS Code et d'Eclipse, compile mal ces constructeurs générés en Java 17 : il signale à tort une erreur « Flexible Constructor Bodies » et produit une classe qui échoue à l'exécution (« Unresolved compilation problems »). Le problème est connu ([projectlombok/lombok#4086](https://github.com/projectlombok/lombok/issues/4086), [eclipse-jdt/eclipse.jdt.core#5489](https://github.com/eclipse-jdt/eclipse.jdt.core/issues/5489)) et touche aussi la dernière version de Lombok. L'IDE et Maven partageant le dossier `target/classes`, il a même fait échouer des tests Maven.

## Décision

- Les constructeurs d'injection sont **écrits à la main**, ce qui est l'injection par constructeur standard de Spring.
- Lombok reste utilisé pour ce qui ne génère pas de constructeur : `@Getter`, `@Setter`, `@Slf4j`.

## Alternatives envisagées

- **Monter le niveau de langage en Java 25** : impossible, l'énoncé impose Java 17.
- **Attendre le correctif de l'IDE** : le projet doit fonctionner chez le jury, quel que soit son environnement.

## Conséquences

- Le projet fonctionne à l'identique, qu'il soit compilé par Maven, VS Code ou Eclipse.
- Chaque service compte quelques lignes de plus.
