package ci.orange.subscription.domain.subscription;

import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * A transition outside of the allowed schema. The message states the current and the required
 * statuses, as the specification asks.
 */
@Getter
public class InvalidStatusTransitionException extends BusinessRuleViolationException {

	private final SubscriptionTransition transition;
	private final SubscriptionStatus currentStatus;

	public InvalidStatusTransitionException(SubscriptionTransition transition, SubscriptionStatus currentStatus) {
		super(DomainErrorCode.INVALID_STATUS_TRANSITION,
				"Opération %s impossible : la souscription est au statut %s, statut requis : %s".formatted(transition, currentStatus, describe(transition.requiredStatuses())));
		this.transition = transition;
		this.currentStatus = currentStatus;
	}

	public Set<SubscriptionStatus> getRequiredStatuses() {
		return transition.requiredStatuses();
	}

	private static String describe(Set<SubscriptionStatus> statuses) {
		return statuses.stream().map(Enum::name).collect(Collectors.joining(" ou "));
	}

}
