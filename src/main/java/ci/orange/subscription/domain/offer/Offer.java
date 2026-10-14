package ci.orange.subscription.domain.offer;

import static ci.orange.subscription.domain.common.DomainValidation.requireText;
import static ci.orange.subscription.domain.common.DomainValidation.requireValue;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * An offer references its technology by code only: whether that technology exists in the
 * catalogue is checked by the application layer.
 */
@Getter
public class Offer {

	/** Upper-case letters and digits, optionally separated by hyphens, e.g. FTTH-100 (rule 7). */
	public static final String CODE_REGEX = "^[A-Z0-9]+(-[A-Z0-9]+)*$";

	private static final Pattern CODE_PATTERN = Pattern.compile(CODE_REGEX);

	private final Long id;
	private final String code;
	private String label;
	private final String technology;
	private BigDecimal monthlyPrice;
	private boolean active;
	private final AuditInfo audit;

	private Offer(Long id, String code, String label, String technology, BigDecimal monthlyPrice, boolean active,
			AuditInfo audit) {
		this.id = id;
		this.code = code;
		this.label = label;
		this.technology = technology;
		this.monthlyPrice = monthlyPrice;
		this.active = active;
		this.audit = audit;
	}

	/** A new offer is always active (rule 9). */
	public static Offer create(String code, String label, String technology, BigDecimal monthlyPrice) {
		return new Offer(null, requireCode(code), requireText(label, "label"), requireText(technology, "technology"),
				requirePositivePrice(monthlyPrice), true, null);
	}

	public static Offer restore(Long id, String code, String label, String technology, BigDecimal monthlyPrice,
			boolean active, AuditInfo audit) {
		return new Offer(id, code, label, technology, monthlyPrice, active, audit);
	}

	/** Code and technology are not part of the update: they cannot change after creation (rule 10). */
	public void update(String label, BigDecimal monthlyPrice) {
		this.label = requireText(label, "label");
		this.monthlyPrice = requirePositivePrice(monthlyPrice);
	}

	/** Existing subscriptions are not affected; new ones are refused (rule 11). */
	public void deactivate() {
		this.active = false;
	}

	private static String requireCode(String code) {
		String value = requireText(code, "code");
		if (!CODE_PATTERN.matcher(value).matches()) {
			throw new DomainValidationException(DomainErrorCode.INVALID_OFFER_CODE,
					"Le code de l'offre doit être en majuscules (lettres, chiffres et tirets, ex. FTTH-100)");
		}
		return value;
	}

	/** Amounts always have two decimals, as stored: a third one is refused rather than silently rounded. */
	private static BigDecimal requirePositivePrice(BigDecimal monthlyPrice) {
		requireValue(monthlyPrice, "monthlyPrice");
		if (monthlyPrice.signum() <= 0) {
			throw new DomainValidationException(DomainErrorCode.INVALID_MONTHLY_PRICE,
					"Le prix mensuel doit être strictement positif");
		}
		try {
			return monthlyPrice.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException exception) {
			throw new DomainValidationException(DomainErrorCode.INVALID_MONTHLY_PRICE,
					"Le prix mensuel doit comporter au plus deux décimales");
		}
	}

}
