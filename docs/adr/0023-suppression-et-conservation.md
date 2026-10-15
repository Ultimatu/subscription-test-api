# ADR-0023 — Suppression définitive et durée de conservation des données

- **Statut** : Acceptée et mise en œuvre (15/10/2026). Complète l'[ADR-0005](0005-suppression-logique.md).

## Contexte

Jusqu'ici, toute suppression était logique ([ADR-0005](0005-suppression-logique.md)) : les données restaient en base indéfiniment. Ce n'est pas ce que demande la loi ivoirienne sur la protection des données personnelles, dont l'ARTCI est l'autorité de contrôle. La loi n'impose pas la suppression logique ; elle impose de ne garder les données que le temps nécessaire, puis de les effacer, sauf obligation légale de les conserver.

Loi n° 2013-450 du 19 juin 2013 relative à la protection des données à caractère personnel, d'après le texte publié par l'AFAPDP (la numérotation des articles est à confirmer au Journal officiel) :

- **article 16** : les données « doivent être conservées pendant une durée qui n'excède pas la période nécessaire aux finalités pour lesquelles elles ont été collectées ou traitées » ;
- **article 33** : la personne concernée a le droit d'obtenir l'effacement de ses données, notamment lorsqu'elles ne sont plus nécessaires ou que le délai de conservation autorisé a expiré ;
- **article 35** : l'effacement a lieu sans délai, « sauf lorsque la conservation des données […] est nécessaire […] au respect d'une obligation légale de conserver les données » ;
- **article 36** : le responsable du traitement met en place les mécanismes d'effacement ou examine périodiquement la nécessité de conserver les données ; une fois l'effacement effectué, il ne les traite plus ;
- **article 43** : les données sont conservées pendant une durée fixée par l'Autorité de protection (l'ARTCI), en fonction de la finalité de chaque traitement.

Un opérateur de télécommunications est en outre tenu d'identifier ses abonnés et de conserver leurs données d'identification (ordonnance n° 2012-293 du 21 mars 2012, article 163, et décret n° 2017-193, d'après des sources secondaires : textes et durées à vérifier). Les données d'un client qui a eu une ligne ne peuvent donc pas être effacées dès sa suppression, mais ne doivent pas non plus être gardées sans limite.

C'est une **extension au-delà de l'énoncé**, réalisée parce que le délai le permettait. Le comportement de `DELETE /api/clients/{id}` reste conforme à la règle 6.

## Décision

- **Un client qui n'a jamais souscrit** est supprimé **définitivement** par `DELETE /api/clients/{id}` : il n'a aucune donnée d'abonné à conserver.
- **Un client qui a un historique de souscriptions** (même supprimées) est supprimé **logiquement** : il disparaît de l'API mais reste en base pendant le délai de conservation.
- **La purge** (`DELETE /api/clients/{id}/purge`, réservée au rôle ADMIN) supprime définitivement le client, ses souscriptions, leur historique et leurs notifications. Avec la rétention active, elle n'est permise que pour un client déjà supprimé dont le délai de conservation est écoulé ; sinon, 409 `RETENTION_PERIOD_NOT_ELAPSED`, avec la date à partir de laquelle elle le sera.
- **Une purge automatique** quotidienne efface les clients dont le délai est écoulé (article 16 : rien ne doit être conservé au-delà du nécessaire). Elle ne tourne qu'avec la rétention active.
- `GET /api/clients/deleted` (ADMIN) liste les clients supprimés, avec la date à partir de laquelle chacun pourra être purgé.
- **Chaque mécanisme se règle par la configuration** (`app.deletion.*`, surchargeable par variables d'environnement) :

  | Propriété | Développement | Production recommandée | Rôle |
  |---|---|---|---|
  | `soft-delete` | `true` | `true` | Suppression logique d'un client qui a un historique ; `false` : suppression définitive immédiate |
  | `hard-delete` | `true` | `true` | Purge permise (manuelle et automatique) ; `false` : 409 `HARD_DELETE_DISABLED` |
  | `retention.enabled` | `false` | `true` | Délai de conservation avant la purge, et purge automatique |
  | `retention.period` | `P3Y` | durée fixée par l'ARTCI | Délai compté depuis la suppression logique |

  En développement, la rétention est désactivée : un administrateur peut purger tout de suite, pour tester. **En production, purger sans délai un client qui a eu une ligne exposerait l'opérateur à manquer à son obligation de conservation (article 35)** : la rétention doit y être activée, avec la durée arrêtée par l'ARTCI pour ce traitement. La valeur de 3 ans est indicative, aucune durée n'ayant pu être vérifiée dans les textes.
- Une souscription PENDING supprimée (règle 22) suit la même politique : logique par défaut, définitive si `soft-delete` vaut `false`.

## Alternatives envisagées

- **Tout conserver par suppression logique** (situation précédente) : simple, mais contraire à l'article 16 ; c'est une non-conformité, pas une précaution.
- **Suppression définitive immédiate de tout** : conforme au droit à l'effacement, mais contraire aux obligations de conservation d'un opérateur (article 35).
- **Anonymisation au lieu de la purge** : on effacerait les données personnelles (nom, email, téléphone) en gardant des souscriptions anonymes pour les statistiques, ce que la loi autorise (article 16, alinéa 2). Plus fin, mais l'anonymisation doit être réellement irréversible (un ND ou une ville peuvent suffire à réidentifier un client) : c'est une piste d'amélioration.

## Conséquences

- La purge est irréversible et efface aussi l'historique et les notifications ; les statistiques ne comptent plus les souscriptions purgées.
- La durée de conservation est une décision juridique, pas technique : elle doit être fixée avec le délégué à la protection des données, au vu des textes de l'ARTCI.
- Les lignes supprimées logiquement sont invisibles pour JPA (`@SQLRestriction`) : leur lecture et leur purge passent par du SQL natif, dans l'adaptateur de persistance.
