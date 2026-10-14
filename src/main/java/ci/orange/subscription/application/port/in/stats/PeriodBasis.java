package ci.orange.subscription.application.port.in.stats;

/**
 * Date de la souscription qui doit tomber dans la période des statistiques par statut.
 */
public enum PeriodBasis {

	/** Souscriptions créées dans la période, selon leur statut actuel. */
	CREATION_DATE,

	/** Souscriptions qui démarrent dans la période, selon leur statut actuel. */
	START_DATE,

	/** Souscriptions passées à chaque statut pendant la période, d'après l'historique des statuts. */
	STATUS_CHANGE

}
