package ci.orange.subscription.application.port.in.technology;

import ci.orange.subscription.domain.technology.Technology;
import java.util.List;

public interface TechnologyUseCases {

	/**
	 * Le catalogue, trié par code : il est assez petit pour ne pas être paginé.
	 *
	 * @param active filtre facultatif : seulement les technologies disponibles, ou seulement les retirées
	 */
	List<Technology> list(Boolean active);

	Technology get(String code);

	Technology create(CreateTechnologyCommand command);

	/** Retire la technologie du catalogue : plus de nouvelle offre ni de souscription (ADR-0013). */
	Technology deactivate(String code);

	/** Remet la technologie au catalogue. */
	Technology activate(String code);

}
