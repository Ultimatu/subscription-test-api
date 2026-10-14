# ADR-0017 — Recherche insensible aux accents avec l'extension unaccent

- **Statut** : Acceptée (14/10/2026)

## Contexte

L'énoncé demande une recherche « insensible à la casse et sur correspondance partielle », sans parler des accents. Or les noms et les villes ivoiriens en portent souvent (Koné, Traoré, Bouaké, Séguéla) : un conseiller qui tape « kone » ou « bouake » ne trouvait rien.

## Décision

- L'extension PostgreSQL **`unaccent`** est installée par une migration (V6), explicitement dans le schéma `public`, partagé par l'application et par les tests. Elle est *trusted* depuis PostgreSQL 13 : le propriétaire de la base peut l'installer sans être superutilisateur.
- Toutes les comparaisons de texte saisi passent par **`lower(unaccent(…))`** (classe `TextSearch`) : recherche sur le nom, le prénom et l'email, filtres par ville des clients et des souscriptions, regroupement par ville des statistiques.
- **L'ordre des fonctions compte** : sur une base en locale « C » (valeur par défaut de certaines installations, dont Homebrew), `lower()` ne traite que l'ASCII, et `lower('É')` reste `'É'`. Les accents sont donc retirés d'abord, puis le texte est passé en minuscules. Cette précaution corrige au passage un défaut antérieur : une ville saisie « BOUAKÉ » n'était pas trouvée en cherchant « bouaké ».
- Les données enregistrées gardent leurs accents : seule la comparaison les ignore.

## Alternatives envisagées

- **Une colonne de recherche normalisée**, calculée en Java à chaque enregistrement : aucune dépendance, mais une donnée dupliquée à maintenir pour chaque champ recherché.
- **Retirer les accents des données elles-mêmes** : écarté, cela altère le nom réel des clients.
- **Une collation non déterministe** (insensible aux accents) : PostgreSQL 16 ne la prend pas en charge avec `LIKE`.
- **Ne rien faire** : conforme à l'énoncé, mais une mauvaise expérience pour les noms accentués.

## Conséquences

- « kone », « KONÉ » et « Koné » sont équivalents dans toutes les recherches et tous les filtres.
- **Nouveau prérequis** : PostgreSQL doit disposer du module *contrib*. Il est inclus par défaut dans la plupart des installations (Homebrew, installeur Windows, paquets Debian et Ubuntu, image Docker officielle), mais c'est un paquet séparé sur RHEL et Fedora (`postgresql-contrib`). À défaut, la migration V6 échoue au démarrage. Ce prérequis figure dans le README.
- Les recherches partielles (`LIKE '%…%'`) parcourent toute la table : pour de gros volumes, un index trigramme (`pg_trgm`) serait à ajouter.
