/**
 * Le métier, en Java pur : les modèles qui protègent leurs invariants, les exceptions métier et leurs
 * codes ({@code DomainErrorCode}).
 *
 * <p>Aucun import Spring, JPA, Hibernate ou Jackson ({@code ArchitectureTest} le vérifie). Une règle
 * métier s'écrit ici, dans le modèle qu'elle concerne ({@code Client.create} force le statut ACTIVE,
 * {@code subscription.suspend(reason)} refuse une transition interdite), et non dans un service ou un
 * controller.
 *
 * <p>Une souscription ne garde que l'identifiant de son client et de son offre, pas les objets
 * eux-mêmes : on ne peut donc pas modifier un client en passant par une souscription, et c'est la
 * couche application qui vérifie que ces identifiants existent (ADR-0001).
 */
package ci.orange.subscription.domain;
