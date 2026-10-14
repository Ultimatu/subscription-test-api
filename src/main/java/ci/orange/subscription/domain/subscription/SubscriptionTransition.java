package ci.orange.subscription.domain.subscription;

import static ci.orange.subscription.domain.subscription.SubscriptionStatus.ACTIVE;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.PENDING;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.SUSPENDED;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.TERMINATED;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Les seules transitions de statut autorisées, une constante par opération de l'énoncé. Toute autre
 * transition est refusée. TERMINATED est un état final : aucune opération n'en part.
 */
public enum SubscriptionTransition {

	ACTIVATE(EnumSet.of(PENDING), ACTIVE, false),
	SUSPEND(EnumSet.of(ACTIVE), SUSPENDED, true),
	REACTIVATE(EnumSet.of(SUSPENDED), ACTIVE, false),
	TERMINATE(EnumSet.of(PENDING, ACTIVE, SUSPENDED), TERMINATED, true);

	private final Set<SubscriptionStatus> requiredStatuses;
	private final SubscriptionStatus target;
	private final boolean reasonRequired;

	SubscriptionTransition(Set<SubscriptionStatus> requiredStatuses, SubscriptionStatus target,
			boolean reasonRequired) {
		this.requiredStatuses = Collections.unmodifiableSet(requiredStatuses);
		this.target = target;
		this.reasonRequired = reasonRequired;
	}

	public boolean isAllowedFrom(SubscriptionStatus status) {
		return requiredStatuses.contains(status);
	}

	public Set<SubscriptionStatus> requiredStatuses() {
		return requiredStatuses;
	}

	public SubscriptionStatus target() {
		return target;
	}

	public boolean isReasonRequired() {
		return reasonRequired;
	}

}
