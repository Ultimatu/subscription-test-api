package ci.orange.subscription.domain.subscription;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;
import static ci.orange.subscription.domain.common.DomainValidation.requireValue;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * A subscription references its client and its offer by id only: each one is loaded and checked
 * by the application layer. The reference and the ND never change once assigned (rules 16 to 18).
 */
@Getter
public class Subscription {

	private final Long id;
	private final String reference;
	private final String nd;
	private final Long clientId;
	private final Long offerId;
	private SubscriptionStatus status;
	private final LocalDate startDate;
	private LocalDate endDate;
	private final AuditInfo audit;

	@Getter(AccessLevel.NONE)
	private final List<StatusChange> recordedStatusChanges = new ArrayList<>();

	private Subscription(Long id, String reference, String nd, Long clientId, Long offerId, SubscriptionStatus status,
			LocalDate startDate, LocalDate endDate, AuditInfo audit) {
		this.id = id;
		this.reference = reference;
		this.nd = nd;
		this.clientId = clientId;
		this.offerId = offerId;
		this.status = status;
		this.startDate = startDate;
		this.endDate = endDate;
		this.audit = audit;
	}

	/** A new subscription starts PENDING and cannot start before today (rule 19). */
	public static Subscription create(String reference, String nd, Long clientId, Long offerId, LocalDate startDate,
			LocalDate today) {
		requireValue(startDate, "startDate");
		if (startDate.isBefore(today)) {
			throw new DomainValidationException("La date de début ne peut pas être antérieure à la date du jour");
		}
		Subscription subscription = new Subscription(null, requireText(reference, "reference"), requireText(nd, "nd"),
				requireValue(clientId, "clientId"), requireValue(offerId, "offerId"), SubscriptionStatus.PENDING,
				startDate, null, null);
		subscription.recordedStatusChanges.add(new StatusChange(null, SubscriptionStatus.PENDING, null));
		return subscription;
	}

	public static Subscription restore(Long id, String reference, String nd, Long clientId, Long offerId,
			SubscriptionStatus status, LocalDate startDate, LocalDate endDate, AuditInfo audit) {
		return new Subscription(id, reference, nd, clientId, offerId, status, startDate, endDate, audit);
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

	/** Termination is final and closes the subscription today (rule 21). */
	public void terminate(String reason, LocalDate today) {
		apply(SubscriptionTransition.TERMINATE, reason);
		this.endDate = today;
	}

	/** Only a subscription that has never been active can be deleted; others must be terminated (rule 22). */
	public void ensureDeletable() {
		if (status != SubscriptionStatus.PENDING) {
			throw new BusinessRuleViolationException(
					"Seule une souscription PENDING peut être supprimée (statut courant : %s) ; une souscription ayant été active doit être résiliée"
							.formatted(status));
		}
	}

	/** PENDING or ACTIVE: the subscription holds its technology (rule 14). */
	public boolean isInProgress() {
		return status == SubscriptionStatus.PENDING || status == SubscriptionStatus.ACTIVE;
	}

	public boolean isSuspended() {
		return status == SubscriptionStatus.SUSPENDED;
	}

	/** Status changes made since this object was created or loaded, to be stored in the history. */
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
