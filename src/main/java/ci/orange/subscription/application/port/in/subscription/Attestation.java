package ci.orange.subscription.application.port.in.subscription;

/**
 * Document PDF prêt à être téléchargé ou joint à un email.
 */
public record Attestation(String fileName, byte[] content) {

	public static final String MEDIA_TYPE = "application/pdf";

}
