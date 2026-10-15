package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.application.port.out.AttestationRenderer;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.technology.Technology;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttestationServiceTest {

	private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
	private static final Instant NOW = Instant.parse("2026-10-20T09:30:00Z");

	@Mock
	private SubscriptionQueries subscriptionQueries;

	@Mock
	private ClientRepository clientRepository;

	@Mock
	private TechnologyRepository technologyRepository;

	@Mock
	private AttestationRenderer renderer;

	private AttestationService attestationService;

	private final SubscriptionView view = new SubscriptionView(10L, "SUB-2026-000001", "2700000001",
			SubscriptionStatus.ACTIVE, "FTTH", new BigDecimal("25000.00"), LocalDate.of(2026, 10, 15), null,
			new ClientOverview(1L, "Awa", "Koné", "Abidjan", ClientStatus.ACTIVE),
			new OfferOverview(2L, "FTTH-100", "Fibre Confort 100 Mbps", new BigDecimal("25000.00")), null);

	private final Client holder = Client.restore(1L, "Awa", "Koné", "awa.kone@exemple.ci", "0701020304", "Abidjan",
			ClientStatus.ACTIVE, null);

	@BeforeEach
	void createService() {
		attestationService = new AttestationService(subscriptionQueries, clientRepository, technologyRepository,
				renderer, Clock.fixed(NOW, ABIDJAN));
	}

	@Test
	void rendersTheSubscriptionWithItsHolderHistoryAndTechnologyLabel() {
		List<StatusHistoryEntry> history = List
				.of(new StatusHistoryEntry(null, SubscriptionStatus.PENDING, null, NOW, "conseiller"));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));
		when(subscriptionQueries.findHistory(10L)).thenReturn(history);
		when(clientRepository.findById(1L)).thenReturn(Optional.of(holder));
		when(technologyRepository.findByCode("FTTH"))
				.thenReturn(Optional.of(Technology.restore("FTTH", "Fibre optique", null)));
		byte[] pdf = { 1, 2, 3 };
		when(renderer.render(any())).thenReturn(pdf);

		Attestation attestation = attestationService.generate(10L);

		assertThat(attestation.fileName()).isEqualTo("attestation-SUB-2026-000001.pdf");
		assertThat(attestation.content()).isSameAs(pdf);
		ArgumentCaptor<AttestationRenderer.Content> content = ArgumentCaptor.forClass(AttestationRenderer.Content.class);
		verify(renderer).render(content.capture());
		assertThat(content.getValue()).isEqualTo(new AttestationRenderer.Content(view, history, holder, "Fibre optique",
				ZonedDateTime.ofInstant(NOW, ABIDJAN)));
	}

	@Test
	void fallsBackOnTheTechnologyCodeWhenItsLabelIsUnknown() {
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));
		when(clientRepository.findById(1L)).thenReturn(Optional.of(holder));
		when(technologyRepository.findByCode("FTTH")).thenReturn(Optional.empty());

		attestationService.generate(10L);

		ArgumentCaptor<AttestationRenderer.Content> content = ArgumentCaptor.forClass(AttestationRenderer.Content.class);
		verify(renderer).render(content.capture());
		assertThat(content.getValue().technologyLabel()).isEqualTo("FTTH");
	}

	@Test
	void reportsAnUnknownSubscription() {
		when(subscriptionQueries.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> attestationService.generate(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_FOUND);
		verifyNoInteractions(renderer);
	}

}
