package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.offer.Technology;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the specification code ("4G_BOX") rather than the Java constant name ("BOX_4G").
 */
@Converter
public class TechnologyConverter implements AttributeConverter<Technology, String> {

	@Override
	public String convertToDatabaseColumn(Technology technology) {
		return technology == null ? null : technology.getCode();
	}

	@Override
	public Technology convertToEntityAttribute(String code) {
		return code == null ? null : Technology.fromCode(code);
	}

}
