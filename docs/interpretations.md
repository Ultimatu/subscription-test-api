# Interprétations de l'énoncé

Ce document recense les points sur lesquels l'énoncé laisse une marge d'interprétation, et le choix retenu pour chacun. Les numéros renvoient aux règles métier de la section 3 de l'énoncé.

## Clients

| Règle | Question | Choix retenu |
|---|---|---|
| 1 | L'unicité de l'email tient-elle compte de la casse ? | Non. L'email est enregistré en minuscules : `Awa@Mail.ci` et `awa@mail.ci` sont un doublon (409 `CLIENT_EMAIL_ALREADY_USED`). |
| 1 | Qu'est-ce qu'une « tentative de modification » de l'email ? | La présence d'un champ `email` non nul dans le corps du `PUT`, même s'il est identique à l'email actuel : 400 `VALIDATION_ERROR`. |
| 2 | Format exact du numéro de contact | Dix chiffres commençant par 01, 05 ou 07 : `^0[157][0-9]{8}$`. Ni espaces ni indicatif `+225`. |
| 3 | Que faire d'un `status` fourni à la création ? | Il est ignoré, sans erreur, comme le demande l'énoncé. |
| 6 | Que deviennent les souscriptions résiliées d'un client supprimé ? | Le client et ses souscriptions résiliées sont supprimés logiquement : ils disparaissent de l'API mais restent en base ([ADR-0005](adr/0005-suppression-logique.md)). |
| 6 | Peut-on recréer un client avec l'email d'un client supprimé ? | Oui : c'est un nouveau client, avec un nouvel identifiant. |
| — | Synthèse : quel revenu mensuel total ? | La somme des prix mensuels des souscriptions **ACTIVE** uniquement, les seules facturées. Tous les statuts figurent dans le décompte, y compris à zéro. |
| — | Filtres de la liste | `city` : égalité insensible à la casse. `search` : correspondance partielle, insensible à la casse, sur le prénom, le nom et l'email ; les caractères `%` et `_` sont recherchés littéralement. Les accents ne sont pas ignorés (`kone` ne trouve pas `Koné`). |

## Offres

| Règle | Question | Choix retenu |
|---|---|---|
| 7 | Code en minuscules : convertir ou refuser ? | Refuser (400). Le code accepte majuscules, chiffres et tirets : `^[A-Z0-9]+(-[A-Z0-9]+)*$`. |
| 8 | Nombre de décimales du prix | Deux au plus ; une troisième est refusée plutôt qu'arrondie ([ADR-0011](adr/0011-montants.md)). |
| 9 | `active` fourni à la création | Ignoré, comme le demande l'énoncé. |
| 9 | `active` fourni dans le `PUT` | Refusé (400) : la désactivation passe exclusivement par `PATCH /api/offers/{id}/deactivate`. |
| 10 | `code` ou `technology` fournis dans le `PUT` | Refusés (400), par cohérence avec la règle 1 sur l'email. |
| — | Désactiver une offre déjà inactive | Sans effet : réponse 200 (opération idempotente). |
| — | Technologie absente du catalogue | 400 `UNKNOWN_TECHNOLOGY`. Les technologies sont gérées par `/api/technologies` ([ADR-0006](adr/0006-technologies-donnees-de-reference.md)). |

## Souscriptions

| Règle | Question | Choix retenu |
|---|---|---|
| 12 | Client ou offre de la requête introuvable | 404 (`CLIENT_NOT_FOUND`, `OFFER_NOT_FOUND`), avant les règles métier. |
| 15 | Ordre complet des vérifications à la création | Date de début (400), puis existence du client et de l'offre (404), puis client actif, offre active, absence de souscription suspendue et unicité par technologie (409), dans cet ordre ([ADR-0014](adr/0014-souscriptions-numerotation-et-concurrence.md)). |
| 16 | Quelle année dans la référence `SUB-{ANNÉE}-…` ? | L'année de création de la souscription, et non celle de sa date de début. Le compteur repart à `000001` chaque année. |
| 17 | Mode d'attribution du ND | Séquentiel (`2700000001`, `2700000002`…), unique et jamais réattribué, même après suppression. |
| 19 | Que vaut « la date du jour » ? | La date à Abidjan (fuseau Africa/Abidjan), quel que soit le fuseau du serveur. Une date de début égale à aujourd'hui est acceptée. |
| — | Recherche globale : sens des filtres | `city` porte sur la ville du client (égalité insensible à la casse), `technology` sur la technologie de l'offre souscrite, `nd` sur un ND exact. `startDateFrom` et `startDateTo` sont inclus ; une borne inversée donne une 400 `INVALID_DATE_RANGE`. |
| — | Contenu du détail d'une souscription | Un aperçu du client et de l'offre, plus l'historique complet des statuts avec motifs, auteurs et dates ([ADR-0015](adr/0015-modele-de-lecture-des-souscriptions.md)). |
| — | Souscriptions d'un client inconnu | 404 `CLIENT_NOT_FOUND` plutôt qu'une liste vide. |
| 18 | Qu'est-ce qu'une « tentative de modification » du ND ? | Aucun endpoint ne modifie une souscription : la présence d'un champ `nd` dans n'importe quel corps (création ou transition) donne une 400. Il en va de même pour `reference`, également attribuée par le serveur. |
| 20 | Le motif est-il exigé côté API ? | Oui : `reason` est obligatoire et non vide pour `suspend` et `terminate` (400 sinon), puis conservé dans l'historique. |
| — | Réponse des créations et des transitions | Le détail complet de la souscription après le changement : client, offre et historique. Une transition réussie renvoie 200. |

## Statistiques

| Question | Choix retenu |
|---|---|
| « Souscriptions actives et revenu mensuel par ville » | La ville est celle du client, regroupée sans tenir compte de la casse. Le revenu est la somme des prix mensuels des souscriptions ACTIVE ; seules les villes ayant au moins une souscription active apparaissent, revenu le plus élevé en premier. |
| Quel prix pour le revenu ? | Le prix **actuel** de l'offre : le prix n'est pas figé au moment de la souscription. Pour de la facturation, il faudrait le conserver sur la souscription. |
| « Répartition par technologie » | Nombre de souscriptions ACTIVE et part en pourcentage (deux décimales). Toutes les technologies du catalogue apparaissent, y compris à 0. |
| « Par statut sur une période » : quelle date doit tomber dans la période ? | Au choix de l'appelant, par le paramètre facultatif `basedOn` : `CREATION_DATE` (par défaut : souscriptions créées dans la période, selon leur statut actuel), `START_DATE` (souscriptions qui démarrent dans la période, selon leur statut actuel) ou `STATUS_CHANGE` (souscriptions passées à chaque statut pendant la période, d'après l'historique). |
| Bornes de la période | `from` et `to` sont facultatifs et inclus, interprétés comme des dates à Abidjan. Une période inversée donne une 400 `INVALID_DATE_RANGE`. Tous les statuts apparaissent, y compris à 0, et `total` compte des souscriptions distinctes. |
| Souscriptions supprimées | Jamais comptées. |

## Général

| Question | Choix retenu |
|---|---|
| Ordre des erreurs quand plusieurs problèmes se cumulent | Les données invalides (400) sont signalées avant les conflits (409). |
| Langue des messages | Messages d'erreur en français ; chaque erreur porte en plus un `code` stable en majuscules ([ADR-0007](adr/0007-format-des-erreurs.md)). |
| Pagination | `page` commence à 0, `size` vaut 20 par défaut et 100 au maximum, tri par défaut sur l'identifiant. |
