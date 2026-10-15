package ci.orange.subscription;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Classement des tests (ADR-0026) : un test qui démarre la base de données doit porter l'étiquette
 * des tests d'intégration. Sans elle, il serait compté parmi les tests unitaires et fausserait leur
 * mesure de couverture, sans aucune alerte.
 */
@AnalyzeClasses(packages = "ci.orange.subscription", importOptions = ImportOption.OnlyIncludeTests.class)
class TestClassificationTest {

	private static final DescribedPredicate<JavaAnnotation<?>> INTEGRATION_TAG = DescribedPredicate.describe(
			"@Tag(\"" + IntegrationTest.TAG + "\")",
			annotation -> annotation.getRawType().isEquivalentTo(Tag.class)
					&& annotation.get("value").map(IntegrationTest.TAG::equals).orElse(false));

	@ArchTest
	static final ArchRule testsStartingTheDatabaseAreTaggedAsIntegrationTests = classes()
			.that().areNotAnnotations()
			.and(metaAnnotatedWith(SpringBootTest.class).or(metaAnnotatedWith(DataJpaTest.class)))
			.should().beMetaAnnotatedWith(INTEGRATION_TAG)
			.because("use @IntegrationTest or @PersistenceAdapterTest, so that "
					+ "mvn test -DexcludedGroups=integration runs the unit tests only");

}
