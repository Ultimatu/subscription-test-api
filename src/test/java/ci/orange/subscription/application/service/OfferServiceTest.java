package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.offer.CreateOfferCommand;
import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.in.offer.UpdateOfferCommand;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

	@Mock
	private OfferRepository offerRepository;

	@Mock
	private TechnologyRepository technologyRepository;

	@InjectMocks
	private OfferService offerService;

	@Test
	void createsAnActiveOffer() {
		when(technologyRepository.existsByCode("FTTH")).thenReturn(true);
		when(offerRepository.existsByCode("FTTH-2000")).thenReturn(false);
		when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Offer offer = offerService.create(command("FTTH-2000", "FTTH"));

		assertThat(offer.isActive()).isTrue();
		assertThat(offer.getCode()).isEqualTo("FTTH-2000");
	}

	@Test
	void refusesATechnologyAbsentFromTheCatalogue() {
		when(technologyRepository.existsByCode("5G_BOX")).thenReturn(false);

		assertThatThrownBy(() -> offerService.create(command("BOX5G-100", "5G_BOX")))
				.isInstanceOf(DomainValidationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.UNKNOWN_TECHNOLOGY)
				.hasMessageContaining("5G_BOX");
		verify(offerRepository, never()).save(any());
	}

	@Test
	void refusesAnExistingCode() {
		when(technologyRepository.existsByCode("FTTH")).thenReturn(true);
		when(offerRepository.existsByCode("FTTH-100")).thenReturn(true);

		assertThatThrownBy(() -> offerService.create(command("FTTH-100", "FTTH")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.OFFER_CODE_ALREADY_USED);
		verify(offerRepository, never()).save(any());
	}

	@Test
	void delegatesTheSearch() {
		OfferSearchCriteria criteria = new OfferSearchCriteria("FTTH", true);
		Page<Offer> page = new PageImpl<>(List.of(existingOffer(true)));
		when(offerRepository.search(criteria, PageRequest.of(0, 20))).thenReturn(page);

		assertThat(offerService.search(criteria, PageRequest.of(0, 20))).isSameAs(page);
	}

	@Test
	void reportsAnUnknownOffer() {
		when(offerRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> offerService.get(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.OFFER_NOT_FOUND);
	}

	@Test
	void updatesLabelAndPrice() {
		when(offerRepository.findById(1L)).thenReturn(Optional.of(existingOffer(true)));
		when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Offer offer = offerService.update(1L, new UpdateOfferCommand("Fibre Confort Plus", new BigDecimal("27000")));

		assertThat(offer.getLabel()).isEqualTo("Fibre Confort Plus");
		assertThat(offer.getMonthlyPrice()).isEqualByComparingTo("27000");
		assertThat(offer.getCode()).isEqualTo("FTTH-100");
	}

	@Test
	void deactivatesAnActiveOffer() {
		when(offerRepository.findById(1L)).thenReturn(Optional.of(existingOffer(true)));
		when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(offerService.deactivate(1L).isActive()).isFalse();
	}

	@Test
	void deactivatingAnInactiveOfferChangesNothing() {
		Offer inactive = existingOffer(false);
		when(offerRepository.findById(1L)).thenReturn(Optional.of(inactive));

		assertThat(offerService.deactivate(1L)).isSameAs(inactive);
		verify(offerRepository, never()).save(any());
	}

	private static CreateOfferCommand command(String code, String technology) {
		return new CreateOfferCommand(code, "Libellé", technology, new BigDecimal("25000"));
	}

	private static Offer existingOffer(boolean active) {
		return Offer.restore(1L, "FTTH-100", "Fibre Confort 100 Mbps", "FTTH", new BigDecimal("25000"), active, null);
	}

}
