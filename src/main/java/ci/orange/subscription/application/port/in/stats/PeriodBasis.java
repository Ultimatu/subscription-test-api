package ci.orange.subscription.application.port.in.stats;

/**
 * Which date of a subscription must fall within the period of the status statistics.
 */
public enum PeriodBasis {

	/** Subscriptions created in the period, by current status. */
	CREATION_DATE,

	/** Subscriptions starting in the period, by current status. */
	START_DATE,

	/** Subscriptions that entered each status in the period, read from the status history. */
	STATUS_CHANGE

}
