package ci.orange.subscription.application.port.in.technology;

import ci.orange.subscription.domain.technology.Technology;
import java.util.List;

public interface TechnologyUseCases {

	/** The whole catalogue, sorted by code: it is small enough not to be paginated. */
	List<Technology> list();

	Technology get(String code);

	Technology create(CreateTechnologyCommand command);

}
