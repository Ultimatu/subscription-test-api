package ci.orange.subscription.application.port.in.technology;

import ci.orange.subscription.domain.technology.Technology;
import java.util.List;

public interface TechnologyUseCases {

	/** Tout le catalogue, trié par code : il est assez petit pour ne pas être paginé. */
	List<Technology> list();

	Technology get(String code);

	Technology create(CreateTechnologyCommand command);

}
