package ci.orange.subscription.infrastructure.persistence.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PersistenceAdapterTest
class OfferPersistenceAdapterTest {

	@Autowired
	private OfferRepository offerRepository;

	@Test
	void containsTheCatalogueOfTheSpecification() {
		assertThat(searchCodes(new OfferSearchCriteria(null, null))).containsExactlyInAnyOrder("FTTH-30", "FTTH-100",
				"FTTH-200", "FTTH-500", "FTTH-1000", "SAT-20", "SAT-50", "SAT-100", "BOX4G-50", "BOX4G-150", "BOX4G-UNL");
	}

	@Test
	void keepsTheLabelsAndPricesOfTheSpecification() {
		Offer offer = offerRepository.search(new OfferSearchCriteria("SATELLITE", null), PageRequest.of(0, 20))
				.filter(candidate -> candidate.getCode().equals("SAT-20"))
				.stream().findFirst().orElseThrow();

		assertThat(offer.getLabel()).isEqualTo("Satellite Decouverte 20 Mbps");
		assertThat(offer.getMonthlyPrice()).isEqualByComparingTo("35000");
		assertThat(offer.isActive()).isTrue();
	}

	@Test
	void filtersByTechnologyAndActiveFlag() {
		Offer offer = offerRepository.save(Offer.create("BOX4G-TEST", "Test", "4G_BOX", new BigDecimal("1000")));
		offer.deactivate();
		offerRepository.save(offer);

		assertThat(searchCodes(new OfferSearchCriteria("4G_BOX", true)))
				.containsExactly("BOX4G-150", "BOX4G-50", "BOX4G-UNL");
		assertThat(searchCodes(new OfferSearchCriteria(null, false))).containsExactly("BOX4G-TEST");
	}

	@Test
	void savesAndUpdatesAnOffer() {
		Offer offer = offerRepository.save(Offer.create("FTTH-2000", "Fibre 2 Gbps", "FTTH", new BigDecimal("200000")));
		offer.update("Fibre Ultra 2 Gbps", new BigDecimal("190000.50"));

		Offer updated = offerRepository.save(offer);

		assertThat(updated.getId()).isEqualTo(offer.getId());
		assertThat(updated.getLabel()).isEqualTo("Fibre Ultra 2 Gbps");
		assertThat(updated.getMonthlyPrice()).isEqualByComparingTo("190000.50");
		assertThat(updated.getAudit().createdAt()).isNotNull();
		assertThat(offerRepository.existsByCode("FTTH-2000")).isTrue();
	}

	@Test
	void theDatabaseRejectsADuplicateCodeThatSlippedThroughTheCheck() {
		assertThatThrownBy(() -> offerRepository
				.save(Offer.create("FTTH-100", "Doublon", "FTTH", new BigDecimal("1000"))))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.OFFER_CODE_ALREADY_USED);
	}

	private List<String> searchCodes(OfferSearchCriteria criteria) {
		return offerRepository.search(criteria, PageRequest.of(0, 50, Sort.by("code")))
				.map(Offer::getCode)
				.getContent();
	}

}
