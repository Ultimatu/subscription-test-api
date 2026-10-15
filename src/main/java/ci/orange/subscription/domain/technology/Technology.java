package ci.orange.subscription.domain.technology;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Une technologie d'accès du catalogue (FTTH, SATELLITE, 4G_BOX…). Donnée de référence : son code
 * l'identifie et ne change jamais. Retirée du catalogue, elle n'accepte plus de nouvelle offre ni de
 * nouvelle souscription, sans effet sur l'existant (ADR-0022), comme une offre désactivée (règle 11).
 */
@Getter
public class Technology {

	/** Majuscules, chiffres et tirets bas, par exemple 4G_BOX. */
	public static final String CODE_REGEX = "^[A-Z0-9_]+$";

	public static final int CODE_MAX_LENGTH = 20;

	private static final Pattern CODE_PATTERN = Pattern.compile(CODE_REGEX);

	private final String code;
	private final String label;
	private boolean active;
	private final AuditInfo audit;

	private Technology(String code, String label, boolean active, AuditInfo audit) {
		this.code = code;
		this.label = label;
		this.active = active;
		this.audit = audit;
	}

	/** Une nouvelle technologie est disponible immédiatement. */
	public static Technology create(String code, String label) {
		return new Technology(requireCode(code), requireText(label, "label"), true, null);
	}

	public static Technology restore(String code, String label, boolean active, AuditInfo audit) {
		return new Technology(code, label, active, audit);
	}

	/** Sans effet si la technologie est déjà retirée. */
	public void deactivate() {
		this.active = false;
	}

	/** Sans effet si la technologie est déjà disponible. */
	public void activate() {
		this.active = true;
	}

	/** Une nouvelle offre ou une nouvelle souscription exige une technologie disponible. */
	public void ensureActive() {
		if (!active) {
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_INACTIVE,
					"La technologie %s est retirée du catalogue : elle n'accepte plus de nouvelle offre ni de souscription"
							.formatted(code));
		}
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
