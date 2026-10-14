package ci.orange.subscription.domain.technology;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * An access technology of the catalogue (FTTH, SATELLITE, 4G_BOX...). Reference data: its code
 * identifies it and never changes.
 */
@Getter
public class Technology {

	/** Upper-case letters, digits and underscores, e.g. 4G_BOX. */
	public static final String CODE_REGEX = "^[A-Z0-9_]+$";

	public static final int CODE_MAX_LENGTH = 20;

	private static final Pattern CODE_PATTERN = Pattern.compile(CODE_REGEX);

	private final String code;
	private final String label;
	private final AuditInfo audit;

	private Technology(String code, String label, AuditInfo audit) {
		this.code = code;
		this.label = label;
		this.audit = audit;
	}

	public static Technology create(String code, String label) {
		return new Technology(requireCode(code), requireText(label, "label"), null);
	}

	public static Technology restore(String code, String label, AuditInfo audit) {
		return new Technology(code, label, audit);
	}

	private static String requireCode(String code) {
		String value = requireText(code, "code");
		if (value.length() > CODE_MAX_LENGTH || !CODE_PATTERN.matcher(value).matches()) {
			throw new DomainValidationException(DomainErrorCode.INVALID_TECHNOLOGY_CODE,
					"Le code de la technologie doit comporter au plus %d caractères parmi majuscules, chiffres et _ (ex. 4G_BOX)"
							.formatted(CODE_MAX_LENGTH));
		}
		return value;
	}

}
