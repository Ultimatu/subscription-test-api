# ADR-0006 — Les technologies sont des données de référence, pas un enum

- **Statut** : Acceptée (14/10/2026), remplace le choix initial d'un enum Java. Unicité du libellé ajoutée le 19/10/2026. Le retrait d'une technologie du catalogue, ajouté au-delà de l'énoncé, est décrit dans l'[ADR-0013](0013-extensions.md#6-retrait-dune-technologie-du-catalogue).

## Contexte

L'énoncé liste trois technologies : FTTH, SATELLITE et 4G_BOX. Elles étaient d'abord modélisées par un enum Java, ce qui imposait en plus une correspondance artificielle, `4G_BOX` n'étant pas un identifiant Java valide (constante `BOX_4G`). Surtout, lancer une nouvelle technologie (une box 5G, par exemple) aurait demandé une modification du code et un redéploiement.

## Décision

Règle appliquée dans tout le projet : **un enum quand le code se comporte différemment selon la valeur** (les statuts, qui pilotent la machine à états), **une table quand la valeur n'est qu'une classification**. Aucune règle métier ne dépend d'une technologie particulière : la règle 14 et les statistiques fonctionnent avec n'importe quelle valeur.

- Une table **`technology`** (migration V4), dont le code sert de clé primaire, initialisée avec les trois technologies de l'énoncé.
- Une offre référence sa technologie **par son code**, garanti par une clé étrangère.
- Un catalogue géré par l'API : `GET /api/technologies` et `POST /api/technologies` (réservé aux administrateurs).
- Un **libellé unique**, comparé sans tenir compte de la casse ni des accents : deux codes libellés « Fibre optique » seraient presque toujours une erreur de saisie, et rendraient ambiguë toute liste de choix (409 `TECHNOLOGY_LABEL_ALREADY_USED`, vérifié après l'unicité du code). La base le garantit par un index unique (migration V13) sur le libellé sans accents puis en minuscules, comme la recherche ([ADR-0017](0017-recherche-insensible-aux-accents.md)). PostgreSQL refusant `unaccent()` dans un index (fonction déclarée STABLE, car son dictionnaire dépend du `search_path`), la migration l'enrobe dans une fonction `immutable_unaccent` qui nomme son dictionnaire.

## Alternatives envisagées

- **Garder l'enum** : défendable puisque l'énoncé donne une liste fermée, mais chaque nouvelle technologie devient un chantier de développement.
- **Index unique sur `lower(label)` seul**, sans fonction d'enrobage : plus simple, mais un libellé saisi avec accents (« Téléphonie fixe ») passerait à côté du même libellé écrit sans accents, comme ceux de l'énoncé.

## Conséquences

- Une nouvelle technologie s'ajoute par un simple appel d'API, sans développeur ni déploiement.
- Le contrat d'API ne change pas : on échange toujours les codes `"FTTH"`, `"SATELLITE"`, `"4G_BOX"`.
- Une technologie inconnue n'est plus détectée à la compilation mais à l'exécution, par une vérification qui renvoie une erreur 400 `UNKNOWN_TECHNOLOGY`.
- Une base qui contenait déjà des libellés en double (données de test) est corrigée par la migration V13 : la technologie la plus ancienne garde son libellé, les autres reçoivent leur code en suffixe. La migration ne peut donc pas échouer sur une base existante, et aucune donnée n'est perdue.
