# ADR-0020 — Exports CSV des clients et des souscriptions

- **Statut** : Acceptée et mise en œuvre (15/10/2026)

## Contexte

Les administrateurs du back-office ont besoin de sortir des listes vers un tableur pour leurs reportings : les clients d'une ville, les souscriptions actives d'une technologie, celles qui démarrent ce mois-ci. L'énoncé ne le demande pas : c'est une **extension au-delà de l'énoncé**, réalisée parce que le délai laissé pour le test le permettait. Elle ajoute des endpoints sans en modifier aucun.

## Décision

- **Deux endpoints dédiés** : `GET /api/clients/export` et `GET /api/subscriptions/export`, avec les mêmes filtres que la liste ou la recherche correspondante, sans pagination.
- **Réservés au rôle ADMIN** : un export, ce sont des données personnelles en masse (noms, emails, téléphones). Chaque export est tracé dans les logs : auteur, nombre de lignes et **noms** des filtres utilisés, sans leurs valeurs, pour la même raison que la ligne de log d'une requête omet ses paramètres ([ADR-0008](0008-tracabilite-des-requetes.md)).
- **Format CSV pensé pour Excel en français** : séparateur `;` (la virgule y sert de séparateur décimal), UTF-8 précédé d'une marque d'ordre des octets (sans elle, Excel affiche mal les accents), fins de ligne CRLF, montants `25000,00`, dates `15/10/2026`, téléphones groupés par deux (`07 01 02 03 04`) pour qu'Excel ne supprime pas le 0 initial. Fichier nommé avec la date du jour (`souscriptions-2026-10-15.csv`).
- **Injection de formule** : une valeur saisie par un utilisateur et commençant par `=`, `+`, `-` ou `@` serait exécutée comme une formule à l'ouverture dans un tableur. Elle est préfixée d'une apostrophe, qui la fait lire comme du texte (recommandation de l'OWASP, *CSV Injection*).
- **Lecture par lots, écriture au fil de l'eau** : le cas d'usage renvoie un `BatchedExport`, que l'adaptateur web parcourt en écrivant chaque lot dans la réponse. Les lots font 500 lignes et sont lus par **pagination sur l'identifiant** (« keyset » : les lignes d'identifiant supérieur au dernier lu), chacun dans sa propre transaction, courte, en une seule requête (le client et l'offre d'une souscription sont chargés par la même requête, ce que vérifie un test). La mémoire utilisée ne dépend pas du nombre de lignes.
- **Les critères sont vérifiés avant l'écriture** : une période inversée donne une 400 au format habituel, avant que la réponse commence.

## Alternatives envisagées

- **Négociation de contenu** (`Accept: text/csv` sur `GET /api/subscriptions`) : plus « REST », mais moins visible dans Swagger et Postman, plus difficile à déclencher depuis un navigateur, et elle mêlerait pagination et export dans le même endpoint.
- **XLSX** (Apache POI) : un vrai classeur, mais une dépendance lourde pour un gain limité ; le CSV s'ouvre partout. À ajouter si les utilisateurs le demandent.
- **Pagination par numéro de page** (`OFFSET`) : chaque page demande une requête de comptage, coûte de plus en plus cher en avançant, et une ligne ajoutée ou supprimée pendant l'export décale les pages suivantes (lignes en double ou manquantes).
- **Un flux JPA unique** (`Stream<Entity>`) : une seule requête, mais une transaction et une connexion à la base gardées ouvertes pendant tout le téléchargement, aussi lent que soit le client.
- **Export ouvert aux conseillers** : plus pratique au quotidien, mais contraire au principe du moindre privilège pour des données personnelles en masse. Il suffirait de changer une ligne de `SecurityConfig`.

## Conséquences

- Les exports n'ont pas de limite de taille, et leur coût mémoire reste constant.
- Si le client interrompt le téléchargement, l'export s'arrête au lot suivant.
- Une erreur survenant au milieu d'un export (base indisponible) ne peut plus être signalée par un code HTTP, la réponse étant déjà commencée : le fichier est alors tronqué. C'est le compromis de toute réponse envoyée au fil de l'eau.
