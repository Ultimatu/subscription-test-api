package ci.orange.subscription.infrastructure.notification;

import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.NotificationSender;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.infrastructure.document.DocumentFormats;

/**
 * Contenu d'un email de notification, déjà rédigé et mis en forme : le gabarit HTML et la version
 * texte ne font qu'afficher. Le statut affiché est celui qu'annonce l'email, et non le statut actuel :
 * l'email est parfois envoyé après un changement de statut plus récent.
 */
record NotificationEmail(
		String subject,
		String title,
		String greeting,
		String message,
		String nd,
		String offerLabel,
		String monthlyPrice,
		String statusLabel,
		String dateLabel,
		String date,
		String reason,
		String attachmentNote,
		String nextStep) {

	static NotificationEmail from(NotificationSender.Message message) {
		SubscriptionView subscription = message.subscription();
		String nd = DocumentFormats.inPairs(subscription.nd());
		String offer = subscription.offer().label();
		Wording wording = switch (message.transition()) {
			case ACTIVATE -> new Wording("Votre ligne " + nd + " est active", "Votre ligne est active",
					"Votre souscription à l'offre " + offer + " est active. Bienvenue !",
					"Votre attestation de souscription est jointe à ce message.", null);
			case SUSPEND -> new Wording("Votre ligne " + nd + " est suspendue", "Votre ligne est suspendue",
					"Votre souscription à l'offre " + offer + " est suspendue.", null,
					"Contactez votre conseiller pour régulariser la situation et réactiver votre ligne.");
			case REACTIVATE -> new Wording("Votre ligne " + nd + " est de nouveau active",
					"Votre ligne est de nouveau active",
					"Votre souscription à l'offre " + offer + " est réactivée : votre ligne fonctionne de nouveau.",
					null, null);
			case TERMINATE -> new Wording("Résiliation de votre ligne " + nd, "Votre souscription est résiliée",
					"Votre souscription à l'offre " + offer + " est résiliée. Merci de nous avoir fait confiance.",
					"Votre attestation de résiliation est jointe à ce message.", null);
		};
		boolean terminated = message.transition() == SubscriptionTransition.TERMINATE;
		return new NotificationEmail(wording.subject(), wording.title(),
				"Bonjour " + subscription.client().firstName() + ",", wording.message(), nd, offer,
				DocumentFormats.price(subscription.monthlyPrice()),
				DocumentFormats.statusLabel(message.transition().target()),
				terminated ? "Date de fin" : "Date de début",
				DocumentFormats.day(terminated ? subscription.endDate() : subscription.startDate()), message.reason(),
				message.attachment() == null ? null : wording.attachmentNote(), wording.nextStep());
	}

	/** Version texte, pour les messageries qui n'affichent pas le HTML. */
	String plainText() {
		StringBuilder text = new StringBuilder()
				.append(greeting).append("\n\n")
				.append(message).append("\n\n")
				.append("Numéro de ligne : ").append(nd).append('\n')
				.append("Offre : ").append(offerLabel).append('\n')
				.append("Prix mensuel : ").append(monthlyPrice).append('\n')
				.append("Statut : ").append(statusLabel).append('\n')
				.append(dateLabel).append(" : ").append(date).append('\n');
		if (reason != null) {
			text.append("Motif : ").append(reason).append('\n');
		}
		if (attachmentNote != null) {
			text.append('\n').append(attachmentNote).append('\n');
		}
		if (nextStep != null) {
			text.append('\n').append(nextStep).append('\n');
		}
		return text.append("\n--\nSubscription API. Message de démonstration, merci de ne pas y répondre.\n")
				.toString()
				.replace('\u00A0', ' ');
	}

	/** Ce qui change d'un événement à l'autre. */
	private record Wording(String subject, String title, String message, String attachmentNote, String nextStep) {
	}

}
