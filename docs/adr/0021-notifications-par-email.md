# ADR-0021 — Notifications par email, avec une outbox en base

- **Statut** : Acceptée et mise en œuvre (15/10/2026)

## Contexte

Un opérateur prévient son client quand sa ligne est activée, suspendue, réactivée ou résiliée. L'énoncé ne le demande pas : c'est une **extension au-delà de l'énoncé**, réalisée parce que le délai laissé pour le test le permettait. Elle ne modifie aucun endpoint existant.

Envoyer un email depuis un changement de statut pose deux problèmes classiques :

- **la cohérence** : l'email ne doit partir que si le changement de statut est réellement enregistré, et ne doit pas être perdu s'il l'est ;
- **la disponibilité** : un serveur de messagerie lent ou absent ne doit ni ralentir ni faire échouer l'opération du conseiller.

## Décision

- **Outbox** (table `notification_outbox`, migration V10) : chaque transition enregistre sa notification **dans la même transaction** que le changement de statut. L'adaptateur l'exige (`Propagation.MANDATORY`) : la notification existe si et seulement si le changement est validé.
- **Envoi par une tâche planifiée** (`@Scheduled`, toutes les 10 secondes, désactivable par `app.notifications.enabled`). Elle réserve un lot de 20 notifications dues avec `SELECT … FOR UPDATE SKIP LOCKED` et un bail de 5 minutes : plusieurs instances de l'application ne réservent jamais la même notification. Aucune transaction ne reste ouverte pendant l'envoi ; son résultat est enregistré dans une transaction courte.
- **Nouvelles tentatives** après 1 min, 5 min, 30 min puis 2 h ; ensuite, la notification est abandonnée (`FAILED`) avec la cause du dernier échec. Un échec définitif, comme une souscription supprimée entre-temps avec son client, est abandonné immédiatement.
- **Livraison « au moins une fois »** : si l'application s'arrête entre l'envoi et son enregistrement, l'email peut partir deux fois. C'est le compromis de toute outbox ; l'inverse (« au plus une fois ») perdrait des emails.
- **Contenu** : une version HTML (gabarit Thymeleaf, mise en page en tableaux et styles en ligne, seuls compris par toutes les messageries) et une version texte. L'email annonce le statut atteint par **sa** transition, même s'il part après un changement plus récent. L'attestation PDF ([ADR-0019](0019-attestation-pdf.md)) est jointe à l'activation et à la résiliation, si la souscription est toujours dans cet état : l'attestation décrit l'état actuel, et un email plus récent portera la bonne.
- **Architecture** : le port de sortie `NotificationOutbox` (persistance) et le port de sortie `NotificationSender` (canal d'envoi) ; l'adaptateur `MailNotificationSender` (Spring Mail) dans `infrastructure/notification`. Remplacer l'email par un SMS ne toucherait que cet adaptateur.
- **Exploitation** : délais maximaux de 5 secondes sur les connexions SMTP (sans eux, un serveur muet bloquerait l'envoi indéfiniment) ; contrôle de santé du serveur de messagerie retiré de `/actuator/health`, car l'application fonctionne sans lui ; `GET /api/subscriptions/{id}/notifications` montre l'état de chaque notification.
- **En local** : Mailpit, dans `compose.yaml`, reçoit les emails sans les envoyer et les affiche sur `http://localhost:8025`. **Dans les tests** : GreenMail, un serveur SMTP en mémoire, sans Docker.

## Alternatives envisagées

- **Envoi direct dans la requête** : un serveur de messagerie lent ralentit le conseiller, un serveur absent fait échouer la transition, et un email peut partir alors que la transaction est ensuite annulée.
- **Événement Spring après validation** (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) : plus simple, mais l'email est perdu si l'application s'arrête juste après la validation, sans nouvelle tentative ni trace.
- **Broker de messages** (RabbitMQ, Kafka) ou **file Redis** : adaptés quand plusieurs services consomment les événements, mais un service de plus à exploiter ; et écrire en base puis publier dans le broker pose le même problème de cohérence, que l'on résout justement… par une outbox.
- **Spring Modulith** (registre de publication des événements) : fournit une outbox toute faite, au prix d'une dépendance et de conventions supplémentaires ; l'outbox écrite ici tient en quelques classes et reste lisible.

## Conséquences

- Le client est prévenu en quelques secondes, et aucun email n'est perdu tant que la base de données ne l'est pas.
- Les notifications envoyées s'accumulent : une purge périodique serait à prévoir en production.
- Le contenu est construit au moment de l'envoi, à partir de la souscription : une souscription supprimée avant l'envoi n'est pas notifiée. Figer le contenu dans l'outbox au moment de l'enregistrement lèverait cette limite.
