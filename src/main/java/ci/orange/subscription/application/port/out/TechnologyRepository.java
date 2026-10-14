package ci.orange.subscription.application.port.out;

import ci.orange.subscription.domain.technology.Technology;
import java.util.List;
import java.util.Optional;

public interface TechnologyRepository {

	List<Technology> findAll();

	Optional<Technology> findByCode(String code);

	boolean existsByCode(String code);

	Technology save(Technology technology);

}
