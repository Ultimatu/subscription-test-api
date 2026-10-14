# ADR-0006 — Les technologies sont des données de référence, pas un enum

- **Statut** : Acceptée (14/10/2026) — remplace le choix initial d'un enum Java

## Contexte

L'énoncé liste trois technologies : FTTH, SATELLITE et 4G_BOX. Elles étaient d'abord modélisées par un enum Java, ce qui imposait en plus une correspondance artificielle, `4G_BOX` n'étant pas un identifiant Java valide (constante `BOX_4G`). Surtout, lancer une nouvelle technologie (une box 5G, par exemple) aurait demandé une modification du code et un redéploiement.

## Décision

Règle appliquée dans tout le projet : **un enum quand le code se comporte différemment selon la valeur** (les statuts, qui pilotent la machine à états), **une table quand la valeur n'est qu'une classification**. Aucune règle métier ne dépend d'une technologie particulière : la règle 14 et les statistiques fonctionnent avec n'importe quelle valeur.

- Une table **`technology`** (migration V4), dont le code sert de clé primaire, initialisée avec les trois technologies de l'énoncé.
- Une offre référence sa technologie **par son code**, garanti par une clé étrangère.
- Un catalogue géré par l'API : `GET /api/technologies` et `POST /api/technologies` (réservé aux administrateurs une fois l'authentification en place).

## Alternatives envisagées

- **Garder l'enum** : défendable puisque l'énoncé donne une liste fermée, mais chaque nouvelle technologie devient un chantier de développement.

## Conséquences

- Une nouvelle technologie s'ajoute par un simple appel d'API, sans développeur ni déploiement.
- Le contrat d'API ne change pas : on échange toujours les codes `"FTTH"`, `"SATELLITE"`, `"4G_BOX"`.
- Une technologie inconnue n'est plus détectée à la compilation mais à l'exécution, par une vérification qui renvoie une erreur 400 `UNKNOWN_TECHNOLOGY`.
