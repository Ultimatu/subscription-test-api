# ADR-0001 — Architecture hexagonale, appliquée de façon pragmatique

- **Statut** : Acceptée (14/10/2026). Découpage des ports par responsabilité ajouté le 18/10/2026.

## Contexte

L'API ne gère que trois entités, mais l'essentiel de sa complexité est métier : 22 règles, un cycle de vie des souscriptions avec des transitions interdites, un ordre de vérification imposé. Ces règles doivent pouvoir être testées vite et indépendamment de la base de données et du HTTP.

## Décision

Trois couches, dont les dépendances pointent toujours vers le centre :

- **`domain`** : Java pur, sans aucun import Spring, JPA ou Jackson. Les modèles portent les règles (`Client.create` force le statut ACTIVE, `subscription.suspend(reason)` refuse une transition interdite), ainsi que les exceptions métier et leurs codes.
- **`application`** : les cas d'usage. Les **ports d'entrée** (`ClientUseCases`…) décrivent ce que l'application sait faire, les **ports de sortie** (`ClientRepository`…) ce dont elle a besoin. Les services orchestrent les règles dans le bon ordre et portent les transactions.
- **`infrastructure`** : les adaptateurs. Les controllers REST et la gestion des erreurs dans `web`, les entités JPA et l'implémentation des ports de sortie dans `persistence`, la configuration dans `config`.

Choix pragmatiques, pour ne pas alourdir un projet de cette taille :

- Les ports d'entrée sont **regroupés par agrégat** (`ClientUseCases`…), et non découpés en une interface par cas d'usage. Un agrégat n'a plusieurs ports que lorsqu'une partie de ses cas d'usage évolue pour d'autres raisons (principe de responsabilité unique) : la suppression et la conservation des clients suivent la loi et la configuration, pas la gestion courante, et ont leur port `ClientDeletionUseCases` ; l'attestation et les notifications ont aussi les leurs, à côté de `SubscriptionUseCases`.
- Le modèle du domaine et l'entité JPA sont **deux classes distinctes** (`Client` / `ClientEntity`), reliées par un **mapping manuel** plutôt que par MapStruct : reconstituer un objet métier qui protège ses invariants est plus clair à la main.
- Un agrégat en référence un autre **par son identifiant** (une souscription connaît `clientId`, pas l'objet `Client`). C'est la couche `application` qui vérifie que la référence existe.
- La pagination de Spring Data (`Pageable`, `Page`) est acceptée dans les ports de la couche `application`. C'est une dépendance à `spring-data-commons` et non à JPA ; la remplacer par des types maison n'apporterait que du code de conversion.
- La date du jour vient d'un `Clock` injecté (fuseau Africa/Abidjan), ce qui rend le temps maîtrisable dans les tests.

Les règles de dépendance sont **vérifiées à chaque build** par un test ArchUnit (`ArchitectureTest`) : le domaine ne doit dépendre d'aucun framework, et aucune couche ne doit dépendre d'une couche plus externe.

## Alternatives envisagées

- **Architecture en couches classique** (`controller` / `service` / `repository`) : plus simple, mais les règles métier finissent dans des services qui dépendent de JPA, donc difficiles à tester seules.
- **Organisation par fonctionnalité** (l'équivalent des modules NestJS) : bonne cohésion, mais n'isole pas le métier de la technique.
- **Hexagonale stricte** (une interface par cas d'usage, des types de pagination maison, aucune annotation Spring dans `application`) : plus pure, mais trop de code de cérémonie pour le gain obtenu ici.
- **Un seul port par agrégat, sans exception** : `ClientService` mêlait alors la gestion des clients et la politique de conservation, dans un service de dix méthodes qui changeait pour deux raisons sans rapport.

## Conséquences

- Les règles métier se testent en Java pur, en quelques millisecondes, sans base de données.
- Changer de base de données ou de protocole d'exposition ne toucherait ni le domaine ni les cas d'usage.
- Il y a plus de classes qu'avec une architecture en couches, ainsi qu'une duplication assumée entre modèle et entité. Pour un simple CRUD sans règles, cette architecture serait surdimensionnée ; elle se justifie ici par la richesse du domaine.
- `SubscriptionService` reste un service important (création, transitions, suppression, lectures, export), mais tous ses cas d'usage suivent les règles de l'énoncé sur les souscriptions : il ne serait découpé que si l'un d'eux se mettait à évoluer pour une autre raison.
