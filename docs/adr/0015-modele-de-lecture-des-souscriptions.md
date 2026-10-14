# ADR-0015 — Modèle de lecture distinct pour les souscriptions

- **Statut** : Acceptée (14/10/2026)

## Contexte

Le détail d'une souscription doit présenter « l'offre et le client résumés », et la recherche globale filtre sur la ville du client. Or le modèle du domaine ne connaît son client et son offre que par leurs identifiants ([ADR-0001](0001-architecture-hexagonale.md)). Reconstituer les trois objets du domaine pour chaque ligne d'une page de résultats multiplierait les requêtes (problème N+1), sans aucune règle métier à appliquer, puisqu'on ne fait que lire.

## Décision

Les écritures et les lectures empruntent deux chemins distincts, une séparation lecture/écriture « légère » :

- **Écritures** : port `SubscriptionRepository`, qui manipule le modèle du domaine et ses règles.
- **Lectures** : port `SubscriptionQueries`, qui renvoie directement des **modèles de lecture** (`SubscriptionView`, avec un aperçu du client et de l'offre, et `SubscriptionDetails`, qui y ajoute l'historique des statuts). L'adaptateur de persistance les construit en une seule requête, en chargeant le client et l'offre par jointure (`@EntityGraph`).
- Le détail d'une souscription inclut son **historique complet** (statuts, motifs, auteurs et dates). C'est un champ supplémentaire, qui ne modifie pas le contrat de l'énoncé.

## Alternatives envisagées

- **Lire via le modèle du domaine**, puis charger client et offre séparément : trois requêtes par souscription, et le domaine n'apporte rien à une lecture.
- **CQRS complet**, avec une base ou une projection dédiée à la lecture : sans objet à cette échelle.

## Conséquences

- Une page de résultats se charge en une requête, plus le comptage.
- Les modèles de lecture vivent dans la couche `application` et évoluent librement selon les besoins d'affichage, sans toucher au domaine.
- Deux ports pour un même agrégat, un pour écrire et un pour lire : c'est une séparation à connaître en reprenant le code.
