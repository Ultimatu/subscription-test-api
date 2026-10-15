package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.AttestationUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.AttestationRenderer;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.technology.Technology;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttestationService implements AttestationUseCases {

	private final SubscriptionQueries subscriptionQueries;
	private final ClientRepository clientRepository;
	private final TechnologyRepository technologyRepository;
	private final AttestationRenderer renderer;
	private final Clock clock;

	public AttestationService(SubscriptionQueries subscriptionQueries, ClientRepository clientRepository,
			TechnologyRepository technologyRepository, AttestationRenderer renderer, Clock clock) {
		this.subscriptionQueries = subscriptionQueries;
		this.clientRepository = clientRepository;
		this.technologyRepository = technologyRepository;
		this.renderer = renderer;
		this.clock = clock;
	}

	@Override
	public Attestation generate(Long subscriptionId) {
		SubscriptionView subscription = subscriptionQueries.findById(subscriptionId)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						subscriptionId));
		Long clientId = subscription.client().id();
		Client holder = clientRepository.findById(clientId)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", clientId));
		String technologyLabel = technologyRepository.findByCode(subscription.technology())
				.map(Technology::getLabel)
				.orElse(subscription.technology());

		byte[] pdf = renderer.render(new AttestationRenderer.Content(subscription,
				subscriptionQueries.findHistory(subscriptionId), holder, technologyLabel, ZonedDateTime.now(clock)));
		return new Attestation("attestation-" + subscription.reference() + ".pdf", pdf);
	}

}
