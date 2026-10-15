package ci.orange.subscription.domain.subscription;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;
import static ci.orange.subscription.domain.common.DomainValidation.requireValue;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.offer.Offer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Une souscription ne référence son client et son offre que par leur identifiant : c'est la couche
 * application qui les charge et les vérifie. La référence et le ND ne changent jamais une fois
 * attribués (règles 16 à 18). Le prix mensuel est celui de l'offre au moment de la souscription :
 * une modification ultérieure du prix de l'offre ne le change pas (ADR-0018).
 */
@Getter
public class Subscription {

	private final Long id;
	private final String reference;
	private final String nd;
	private final Long clientId;
	private final Long offerId;
	private final BigDecimal monthlyPrice;
	private SubscriptionStatus status;
	private final LocalDate startDate;
	private LocalDate endDate;
	private final AuditInfo audit;

	@Getter(AccessLevel.NONE)
	private final List<StatusChange> recordedStatusChanges = new ArrayList<>();

	private Subscription(Long id, String reference, String nd, Long clientId, Long offerId, BigDecimal monthlyPrice,
			SubscriptionStatus status, LocalDate startDate, LocalDate endDate, AuditInfo audit) {
		this.id = id;
		this.reference = reference;
		this.nd = nd;
		this.clientId = clientId;
		this.offerId = offerId;
		this.monthlyPrice = monthlyPrice;
		this.status = status;
		this.startDate = startDate;
		this.endDate = endDate;
		this.audit = audit;
	}

	/** Une nouvelle souscription commence au statut PENDING et ne peut pas démarrer avant aujourd'hui (règle 19). */
	public static Subscription create(String reference, String nd, Long clientId, Offer offer, LocalDate startDate,
			LocalDate today) {
		ensureStartDateNotInPast(startDate, today);
		requireValue(offer, "offer");
		Subscription subscription = new Subscription(null, requireText(reference, "reference"), requireText(nd, "nd"),
				requireValue(clientId, "clientId"), requireValue(offer.getId(), "offerId"), offer.getMonthlyPrice(),
				SubscriptionStatus.PENDING, startDate, null, null);
		subscription.recordedStatusChanges.add(new StatusChange(null, SubscriptionStatus.PENDING, null));
		return subscription;
	}

	public static Subscription restore(Long id, String reference, String nd, Long clientId, Long offerId,
			BigDecimal monthlyPrice, SubscriptionStatus status, LocalDate startDate, LocalDate endDate,
			AuditInfo audit) {
		return new Subscription(id, reference, nd, clientId, offerId, monthlyPrice, status, startDate, endDate, audit);
	}

	/** Exposée pour pouvoir refuser la demande avant toute autre vérification (règle 19). */
	public static void ensureStartDateNotInPast(LocalDate startDate, LocalDate today) {
		requireValue(startDate, "startDate");
		if (startDate.isBefore(today)) {
			throw new DomainValidationException(DomainErrorCode.START_DATE_IN_PAST,
					"La date de début ne peut pas être antérieure à la date du jour (%s)".formatted(today));
		}
	}

	public void activate() {
		apply(SubscriptionTransition.ACTIVATE, null);
	}

	public void suspend(String reason) {
		apply(SubscriptionTransition.SUSPEND, reason);
	}

	public void reactivate() {
		apply(SubscriptionTransition.REACTIVATE, null);
	}

	/** La résiliation est définitive et clôt la souscription aujourd'hui (règle 21). */
	public void terminate(String reason, LocalDate today) {
		apply(SubscriptionTransition.TERMINATE, reason);
		this.endDate = today;
	}

	/**
	 * Seule une souscription qui n'a jamais été active peut être supprimée ; les autres doivent
	 * être résiliées (règle 22).
	 */
	public void ensureDeletable() {
		if (status != SubscriptionStatus.PENDING) {
			throw new BusinessRuleViolationException(DomainErrorCode.SUBSCRIPTION_NOT_DELETABLE,
					"Seule une souscription PENDING peut être supprimée (statut courant : %s) ; une souscription ayant été active doit être résiliée"
							.formatted(status));
		}
	}

	/** PENDING ou ACTIVE : la souscription occupe sa technologie (règle 14). */
	public boolean isInProgress() {
		return status == SubscriptionStatus.PENDING || status == SubscriptionStatus.ACTIVE;
	}

	public boolean isSuspended() {
		return status == SubscriptionStatus.SUSPENDED;
	}

	/** Changements de statut faits depuis la création ou le chargement de l'objet, à enregistrer dans l'historique. */
	public List<StatusChange> recordedStatusChanges() {
		return List.copyOf(recordedStatusChanges);
	}

	private void apply(SubscriptionTransition transition, String reason) {
		String checkedReason = transition.isReasonRequired() ? requireText(reason, "reason") : null;
		if (!transition.isAllowedFrom(status)) {
			throw new InvalidStatusTransitionException(transition, status);
		}
		recordedStatusChanges.add(new StatusChange(status, transition.target(), checkedReason));
		this.status = transition.target();
	}

}
