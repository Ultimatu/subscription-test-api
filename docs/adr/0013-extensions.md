# ADR-0013 — Extensions au-delà de l'énoncé

- **Statut** : Acceptées et mises en œuvre (14 et 15/10/2026 ; CORS ajouté le 18/10/2026). Cette fiche regroupe les anciennes fiches 0013 (authentification), 0018 (prix figé), 0019 (attestation), 0020 (exports), 0021 (notifications), 0022 (retrait d'une technologie), 0023 (suppression définitive et conservation) et 0024 (journal d'audit).

## Principe

Ces fonctionnalités ne sont pas demandées par l'énoncé. Elles ont été réalisées parce que le délai laissé pour le test le permettait, à une condition : **ne jamais modifier le contrat d'API de l'énoncé** (mêmes URLs, mêmes corps de requête, mêmes codes HTTP). Elles ajoutent des endpoints, un champ de réponse ou un comportement que le contrat ne voit pas, et suivent la même architecture que le reste ([ADR-0001](0001-architecture-hexagonale.md)) : un port d'entrée pour le cas d'usage, un port de sortie pour la technique, un adaptateur dans `infrastructure`.

| Extension | Ajout à l'API | Migration |
|---|---|---|
| [1. Authentification et rôles](#1-authentification-jwt-et-rôles) | `POST /api/auth/login` | V8 |
| [2. Prix figé à la souscription](#2-prix-figé-à-la-souscription) | champ `monthlyPrice` d'une souscription | V9 |
| [3. Attestation PDF](#3-attestation-pdf) | `GET /api/subscriptions/{id}/attestation` | — |
| [4. Exports CSV](#4-exports-csv) | `GET /api/clients/export`, `GET /api/subscriptions/export` | — |
| [5. Notifications par email](#5-notifications-par-email) | `GET /api/subscriptions/{id}/notifications` | V10 |
| [6. Retrait d'une technologie](#6-retrait-dune-technologie-du-catalogue) | `PATCH /api/technologies/{code}/deactivate` et `/activate` | V11 |
| [7. Suppression définitive et conservation](#7-suppression-définitive-et-durée-de-conservation) | `DELETE /api/clients/{id}/purge`, `GET /api/clients/deleted` | — |
| [8. Journal d'audit](#8-journal-daudit) | `GET /api/audit-events` | V12 |

## 1. Authentification JWT et rôles

**Pourquoi.** L'audit ([ADR-0004](0004-audit-et-historique.md)) n'a de sens que si l'on sait qui agit. L'API est un outil de back-office : ce sont des conseillers et des administrateurs qui l'utilisent, pas les clients finaux.

**Décision.**

- Jeton **JWT** signé en HS256, valable une heure, avec le support natif de Spring Security (`spring-boot-starter-oauth2-resource-server`), sans bibliothèque tierce. `POST /api/auth/login` le délivre ; la signature, l'expiration et l'émetteur sont vérifiés à chaque requête. La clé vient de `APP_SECURITY_JWT_SECRET` hors développement.
- Comptes dans la table `app_user`, mots de passe en empreinte BCrypt. Deux comptes de démonstration : `admin` / `Admin@2026` et `conseiller` / `Conseiller@2026`. Un compte inconnu et un mauvais mot de passe donnent la même réponse (401 `INVALID_CREDENTIALS`).
- Deux rôles, et le principe du moindre privilège :

  | Action | CONSEILLER | ADMIN |
  |---|---|---|
  | Lire clients, offres, technologies, souscriptions ; créer et modifier un client ; souscrire et changer le statut d'une souscription ; attestation et notifications | ✅ | ✅ |
  | Créer, modifier ou désactiver une offre ; gérer les technologies | ❌ | ✅ |
  | Supprimer ou purger ; statistiques ; exports ; journal d'audit | ❌ | ✅ |
  | Connexion, Swagger UI, `/actuator/health` | public | public |

- Erreurs 401 (`AUTHENTICATION_REQUIRED`, `INVALID_TOKEN`) et 403 (`ACCESS_DENIED`) au format Problem Details, comme les autres.
- **CORS fermé par défaut** : seules les origines listées dans `APP_CORS_ALLOWED_ORIGINS` (un back-office publié sur un autre domaine) peuvent appeler `/api/**` depuis un navigateur. Le jeton voyage dans l'en-tête `Authorization`, jamais dans un cookie : aucun identifiant n'est envoyé automatiquement, d'où l'absence de protection CSRF.
- **Désactivable** (`APP_SECURITY_ENABLED=false`), avec un avertissement à chaque démarrage ; la collection Postman se connecte toute seule. Le contrat de l'énoncé reste testable tel quel.

**Écarté.** Un rôle CLIENT, qui supposerait des comptes clients et un filtrage par propriétaire : c'est une autre application. Aucune authentification : conforme à l'énoncé, mais l'audit perdrait son intérêt.

**Limites.** Ni jeton de rafraîchissement, ni révocation, ni gestion des comptes par l'API. En production, l'émission des jetons serait confiée à un fournisseur d'identité (Keycloak, Azure AD…), et la table des comptes disparaîtrait.

## 2. Prix figé à la souscription

**Pourquoi.** Le prix d'une offre est modifiable (règle 10). Lu sur l'offre, il changeait rétroactivement le revenu des souscriptions existantes, et une attestation aurait affiché un prix que le client n'a jamais souscrit.

**Décision.** La souscription conserve son **prix mensuel**, copié de l'offre à la création et jamais modifié : `Subscription.create` reçoit l'offre elle-même, et la colonne n'est pas modifiable par l'application. Les revenus (synthèse d'un client, statistiques par ville) utilisent ce prix. La réponse expose `monthlyPrice` (prix souscrit) à côté de `offer.monthlyPrice` (prix actuel du catalogue) : un champ ajouté, aucun champ changé. La migration V9 reprend, pour les souscriptions existantes, le prix actuel de leur offre.

**Écarté.** Historiser les prix des offres par période de validité : plus général, mais plus complexe pour un seul besoin.

**Conséquence.** Une hausse du catalogue ne s'applique qu'aux nouvelles souscriptions ; l'appliquer aux existantes serait une révision tarifaire, à concevoir comme une opération explicite.

## 3. Attestation PDF

**Pourquoi.** Un conseiller remet au client un document qui atteste sa souscription : numéro de ligne, offre, prix, dates, et, après une résiliation, sa date et son motif. « Attestation » plutôt que « reçu » : le modèle ne connaît ni facture ni paiement.

**Décision.**

- `GET /api/subscriptions/{id}/attestation` renvoie le PDF, nommé d'après la référence. Une souscription résiliée donne une *attestation de résiliation*.
- **Générée à la demande, sans stockage** : toutes les données sont en base et le prix est figé, donc le document régénéré est toujours exact (environ 70 ms).
- Le port d'entrée `AttestationUseCases` rassemble les données ; le port de sortie `AttestationRenderer` les met en page dans `infrastructure/document` : gabarit HTML et CSS (Thymeleaf) converti par **openhtmltopdf**. Les valeurs sont mises en forme en Java (FCFA, dates en français), le gabarit ne fait qu'afficher.
- Police Helvetica (standard du PDF, rien à embarquer), ni logo ni marque réelle, mention « document de démonstration, sans valeur contractuelle ». La première génération, qui indexe les polices du système, est faite au démarrage pour que le premier téléchargement soit immédiat.

**Écarté.** iText (licence AGPL), Flying Saucer (exige Java 21), JasperReports (lourd, format de gabarit propre), génération côté navigateur (chaque client de l'API referait la mise en page, et le document ne pourrait pas être joint à un email), file de traitements (inutile pour une page produite en quelques dizaines de millisecondes ; elle se justifierait pour des relevés en masse).

**Limites.** Le gabarit doit rester du XHTML bien formé, en CSS 2.1 (tableaux, sans flexbox). Les polices standard du PDF ne couvrent que l'alphabet latin.

## 4. Exports CSV

**Pourquoi.** Les administrateurs sortent des listes vers un tableur pour leurs reportings.

**Décision.**

- `GET /api/clients/export` et `GET /api/subscriptions/export`, avec les filtres de la liste correspondante, sans pagination. **Réservés au rôle ADMIN** : ce sont des données personnelles en masse. Chaque export est consigné ([journal d'audit](#8-journal-daudit)) avec son auteur, son nombre de lignes et les **noms** des filtres, sans leurs valeurs.
- Format pour Excel en français : séparateur `;`, UTF-8 avec marque d'ordre des octets, fins de ligne CRLF, montants `25000,00`, dates `15/10/2026`, téléphones groupés par deux pour garder le 0 initial.
- **Injection de formule** : une valeur commençant par `=`, `+`, `-` ou `@` est préfixée d'une apostrophe (recommandation OWASP).
- **Lecture par lots de 500**, par pagination sur l'identifiant (*keyset*), chacun dans une transaction courte et en une seule requête ; écriture au fil de l'eau. La mémoire utilisée ne dépend pas du nombre de lignes. Les critères sont vérifiés avant d'écrire quoi que ce soit.

**Écarté.** Négociation de contenu sur la liste (`Accept: text/csv`) : moins visible dans Swagger et Postman. XLSX : une dépendance lourde pour un gain limité. Pagination par `OFFSET` : de plus en plus coûteuse, et une ligne ajoutée pendant l'export décale les pages. Un flux JPA unique : une transaction ouverte pendant tout le téléchargement.

**Limite.** Une erreur au milieu d'un export ne peut plus changer le code HTTP, déjà envoyé : le fichier est alors tronqué.

## 5. Notifications par email

**Pourquoi.** Un opérateur prévient son client quand sa ligne est activée, suspendue, réactivée ou résiliée. L'email ne doit partir que si le changement est réellement enregistré, ne doit jamais être perdu, et un serveur de messagerie lent ou absent ne doit pas gêner le conseiller.

**Décision.**

- **Outbox** (table `notification_outbox`) : chaque transition enregistre sa notification **dans la même transaction** que le changement de statut (l'adaptateur l'exige, `Propagation.MANDATORY`).
- Une **tâche planifiée** envoie les notifications dues toutes les 10 secondes, par lots de 20 réservés avec `FOR UPDATE SKIP LOCKED` et un bail de 5 minutes : plusieurs instances ne réservent jamais la même. Aucune transaction ne reste ouverte pendant l'envoi.
- Nouvelles tentatives après 1 min, 5 min, 30 min puis 2 h, puis abandon (`FAILED`) avec la cause. Livraison « au moins une fois » : un arrêt entre l'envoi et son enregistrement peut faire partir l'email deux fois, ce qui vaut mieux que de le perdre.
- Email HTML (Thymeleaf, styles en ligne) et version texte. Il annonce le statut atteint par **sa** transition ; l'[attestation](#3-attestation-pdf) est jointe à l'activation et à la résiliation, si la souscription est toujours dans cet état.
- Port de sortie `NotificationSender`, adaptateur Spring Mail : passer au SMS ne toucherait que l'adaptateur. Délais SMTP de 5 secondes ; serveur de messagerie retiré de `/actuator/health`, l'application fonctionnant sans lui. En local, Mailpit (`compose.yaml`) ; dans les tests, GreenMail, sans Docker.

**Écarté.** Envoi direct dans la requête (lent, et un email peut partir pour une transaction annulée). Événement après validation (`@TransactionalEventListener` + `@Async`) : l'email est perdu si l'application s'arrête au mauvais moment. Broker de messages : un service de plus, et le même problème de cohérence, que l'on résout… par une outbox. Spring Modulith : une outbox toute faite, au prix de conventions supplémentaires.

**Limites.** Les notifications envoyées s'accumulent (purge à prévoir). Le contenu est construit à l'envoi : une souscription supprimée entre-temps n'est pas notifiée. Si la purge du client efface la notification pendant son envoi, le résultat n'est simplement pas enregistré, et le reste du lot est envoyé (ajouté le 21/10/2026).

## 6. Retrait d'une technologie du catalogue

**Pourquoi.** Les technologies sont des données de référence ([ADR-0006](0006-technologies-donnees-de-reference.md)) : on peut en ajouter, mais un opérateur arrête aussi de commercialiser une technologie entière, sans pouvoir la supprimer puisque des offres et des souscriptions la référencent.

**Décision.** Une technologie porte un indicateur `active`. `PATCH /api/technologies/{code}/deactivate` la retire du catalogue, `/activate` l'y remet (ADMIN, sans effet si elle est déjà dans l'état demandé) ; `GET /api/technologies?active=` filtre. Une technologie retirée **n'accepte plus de nouvelle offre ni de nouvelle souscription** (409 `TECHNOLOGY_INACTIVE`), sans effet sur l'existant, comme une offre désactivée (règle 11). À la souscription, la vérification se place juste après celle de l'offre active, dont elle partage la nature ; l'ordre de la règle 15 entre les règles de l'énoncé est conservé. La règle est portée par le domaine (`Technology.ensureActive()`).

**Écarté.** Désactiver en cascade les offres de la technologie : irréversible, l'énoncé ne prévoyant pas de réactiver une offre.

## 7. Suppression définitive et durée de conservation

**Pourquoi.** La suppression logique ([ADR-0005](0005-suppression-logique.md)) garde les données sans limite. Or la loi n° 2013-450 sur la protection des données personnelles (texte publié par l'AFAPDP ; numérotation à confirmer au Journal officiel) impose de ne les conserver que le temps nécessaire (article 16), donne droit à leur effacement (article 33), sauf obligation légale de conservation (article 35), interdit de les traiter une fois effacées (article 36) et confie à l'ARTCI la fixation des durées (article 43). Un opérateur doit en outre conserver les données d'identification de ses abonnés (ordonnance n° 2012-293, article 163, et décret n° 2017-193, d'après des sources secondaires : textes et durées à vérifier).

**Décision.**

- `DELETE /api/clients/{id}` reste conforme à la règle 6, mais **un client qui n'a jamais souscrit** est supprimé définitivement : il n'a rien à conserver. Un client qui a un historique est supprimé logiquement.
- **Purge** (`DELETE /api/clients/{id}/purge`, ADMIN) : suppression définitive du client, de ses souscriptions, de leur historique et de leurs notifications. Avec la rétention active, seulement pour un client déjà supprimé dont le délai est écoulé (sinon 409 `RETENTION_PERIOD_NOT_ELAPSED`, avec la date possible). Une **purge automatique** quotidienne efface les clients dont le délai est écoulé. `GET /api/clients/deleted` liste les clients en attente.
- Tout se règle par la configuration (`app.deletion.*`) :

  | Propriété | Développement | Production | Rôle |
  |---|---|---|---|
  | `soft-delete` | `true` | `true` | Suppression logique d'un client qui a un historique |
  | `hard-delete` | `true` | `true` | Purge permise ; sinon 409 `HARD_DELETE_DISABLED` |
  | `retention.enabled` | `false` | `true` | Délai de conservation avant la purge, et purge automatique |
  | `retention.period` | `P3Y` | durée fixée par l'ARTCI | Délai compté depuis la suppression logique |

  En production, purger sans délai un client qui a eu une ligne exposerait l'opérateur à manquer à son obligation de conservation : la rétention doit y être activée. La valeur de 3 ans est indicative, aucune durée n'ayant pu être vérifiée dans les textes.
- Ces cas d'usage ont leur propre port, `ClientDeletionUseCases`, distinct de la gestion courante des clients ([ADR-0001](0001-architecture-hexagonale.md)). Les lignes supprimées logiquement étant invisibles pour JPA, leur lecture et leur purge passent par du SQL natif.

**Écarté.** Tout conserver (contraire à l'article 16). Tout effacer immédiatement (contraire à l'article 35). Anonymiser plutôt que purger : plus fin, mais l'anonymisation doit être réellement irréversible (un ND ou une ville peuvent suffire à réidentifier un client) ; c'est une piste d'amélioration.

**Conséquence.** La purge est irréversible ; les statistiques ne comptent plus les souscriptions purgées. La durée de conservation est une décision juridique, à fixer avec le délégué à la protection des données.

## 8. Journal d'audit

**Pourquoi.** Les colonnes d'audit d'une ligne ([ADR-0004](0004-audit-et-historique.md)) ne disent que sa création et sa dernière modification : une modification efface la trace de la précédente, une suppression fait disparaître la ligne, et rien ne trace un export de données personnelles.

**Décision.**

- Table **`audit_event`**, en ajout seul et sans clé étrangère (le journal survit aux ressources qu'il décrit) : date, auteur, action, ressource, résumé lisible, détails en JSON, identifiant de la requête ([ADR-0008](0008-tracabilite-des-requetes.md)).
- Les services consignent chaque action par le port de sortie `AuditTrail`, **dans la transaction de l'action** : une action annulée ne laisse pas de trace, une action validée en laisse toujours une. Sont consignées les créations, modifications (champs modifiés, avant et après), suppressions et purges de clients, d'offres, de technologies et de souscriptions, chaque changement de statut avec son motif, et chaque export. Une action sans effet n'est pas consignée.
- `GET /api/audit-events` (ADMIN), paginé, filtrable par ressource, auteur, action et période.
- La purge d'un client **efface ses événements** et laisse une trace anonyme (« Client n° 42 purgé ») : la loi interdit tout traitement une fois l'effacement effectué (article 36).

**Écarté.** Hibernate Envers : un historique technique de lignes, pas d'actions métier ; il ne voit pas les exports et garderait les données d'un client purgé. Un intercepteur HTTP : il ne connaît ni le sens métier ni les valeurs avant et après. Les logs : ni interrogeables par l'application, ni conservés selon les mêmes règles.

**Limites.** Le journal grossit sans limite (archivage à définir) ; les connexions ne sont pas consignées.
