package ci.orange.subscription.domain.client;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;
import static ci.orange.subscription.domain.common.DomainValidation.requireValue;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
public class Client {

	/** Ten digits starting with 01, 05 or 07 (rule 2). */
	public static final String PHONE_NUMBER_REGEX = "^0[157][0-9]{8}$";

	private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(PHONE_NUMBER_REGEX);

	private final Long id;
	private String firstName;
	private String lastName;
	private final String email;
	private String phoneNumberClient;
	private String city;
	private ClientStatus status;
	private final AuditInfo audit;

	private Client(Long id, String firstName, String lastName, String email, String phoneNumberClient, String city,
			ClientStatus status, AuditInfo audit) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.phoneNumberClient = phoneNumberClient;
		this.city = city;
		this.status = status;
		this.audit = audit;
	}

	/**
	 * A new client is always ACTIVE, whatever the caller asks (rule 3). The email is normalized so
	 * that uniqueness is case-insensitive.
	 */
	public static Client create(String firstName, String lastName, String email, String phoneNumberClient,
			String city) {
		return new Client(null, requireText(firstName, "firstName"), requireText(lastName, "lastName"),
				requireText(email, "email").toLowerCase(Locale.ROOT), requirePhoneNumber(phoneNumberClient),
				requireText(city, "city"), ClientStatus.ACTIVE, null);
	}

	/** Rebuilds a client loaded from storage, without re-running creation rules. */
	public static Client restore(Long id, String firstName, String lastName, String email, String phoneNumberClient,
			String city, ClientStatus status, AuditInfo audit) {
		return new Client(id, firstName, lastName, email, phoneNumberClient, city, status, audit);
	}

	/** The email is not part of the update: it cannot change after creation (rule 1). */
	public void update(String firstName, String lastName, String phoneNumberClient, String city, ClientStatus status) {
		this.firstName = requireText(firstName, "firstName");
		this.lastName = requireText(lastName, "lastName");
		this.phoneNumberClient = requirePhoneNumber(phoneNumberClient);
		this.city = requireText(city, "city");
		this.status = requireValue(status, "status");
	}

	public boolean isActive() {
		return status == ClientStatus.ACTIVE;
	}

	private static String requirePhoneNumber(String phoneNumber) {
		String value = requireText(phoneNumber, "phoneNumberClient");
		if (!PHONE_NUMBER_PATTERN.matcher(value).matches()) {
			throw new DomainValidationException(
					"Le numéro de téléphone doit comporter dix chiffres et commencer par 01, 05 ou 07");
		}
		return value;
	}

}
