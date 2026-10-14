package ci.orange.subscription;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Règles de l'architecture hexagonale, vérifiées à chaque build plutôt que seulement documentées.
 */
@AnalyzeClasses(packages = "ci.orange.subscription", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..")
			.because("the domain must stay independent from technical frameworks");

	@ArchTest
	static final ArchRule dependenciesPointInwards = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer("Domain").definedBy("..domain..")
			.layer("Application").definedBy("..application..")
			.layer("Infrastructure").definedBy("..infrastructure..")
			.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

}
