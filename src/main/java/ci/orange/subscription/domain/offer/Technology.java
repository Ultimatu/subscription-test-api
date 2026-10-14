package ci.orange.subscription.domain.offer;

import ci.orange.subscription.domain.exception.DomainValidationException;
import java.util.Arrays;

/**
 * Access technology of an offer. The external code (API and database) differs from the constant
 * name because "4G_BOX" is not a valid Java identifier.
 */
public enum Technology {

	FTTH("FTTH"),
	SATELLITE("SATELLITE"),
	BOX_4G("4G_BOX");

	private final String code;

	Technology(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static Technology fromCode(String code) {
		return Arrays.stream(values())
				.filter(technology -> technology.code.equals(code))
				.findFirst()
				.orElseThrow(() -> new DomainValidationException(
						"Technologie inconnue : " + code + " (valeurs possibles : FTTH, SATELLITE, 4G_BOX)"));
	}

}
