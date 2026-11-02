package de.hsh.favs.sharewise;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The layout and naming rules of docs/conventions/01_ARCHITECTURE_AND_NAMING.md. Module
 * boundaries themselves are checked by {@link ModularityTests}. A module joins
 * {@link #CHECKED} when its refactoring to the conventions is done; a rule added to the
 * document is added here in the same commit.
 */
class ConventionTests {

	private static final String ROOT = "de.hsh.favs.sharewise";

	/** Modules that already follow the conventions. */
	private static final Set<String> CHECKED = Set.of("account", "group", "expense", "settlement");

	private static final JavaClasses MAIN = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages(ROOT);

	@TestFactory
	Stream<DynamicTest> everyCheckedModuleFollowsTheLayoutRules() {
		return CHECKED.stream().map(m -> DynamicTest.dynamicTest(m, () -> check(m)));
	}

	private static void check(String module) {
		String base = ROOT + "." + module;
		JavaClasses own = MAIN.that(com.tngtech.archunit.base.DescribedPredicate.describe("in " + module,
				c -> c.getPackageName().equals(base) || c.getPackageName().startsWith(base + ".")));

		// domain depends on neither Spring, servlet, nor the module's other layers
		noClasses().that()
			.resideInAPackage(base + ".domain..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework..", "jakarta.servlet..", base + ".application..",
					base + ".persistence..", base + ".web..")
			.because("the domain is plain Java plus JPA annotations")
			.check(own);

		// layers only point inwards: persistence and application never see web,
		// persistence never sees application
		noClasses().that()
			.resideInAnyPackage(base + ".application..", base + ".persistence..")
			.should()
			.dependOnClassesThat()
			.resideInAPackage(base + ".web..")
			.allowEmptyShould(true)
			.check(own);
		noClasses().that()
			.resideInAPackage(base + ".persistence..")
			.should()
			.dependOnClassesThat()
			.resideInAPackage(base + ".application..")
			.allowEmptyShould(true)
			.check(own);

		// the api package is a pure contract: interfaces, records, enumerations
		classes().that().resideInAPackage(base + ".api").should(beInterfaceRecordOrEnum()).check(own);

		// placement by suffix
		classes().that()
			.haveSimpleNameEndingWith("Controller")
			.should()
			.resideInAPackage(base + ".web")
			.allowEmptyShould(true)
			.check(own);
		classes().that()
			.haveSimpleNameEndingWith("Repository")
			.should()
			.resideInAPackage(base + ".persistence")
			.allowEmptyShould(true)
			.check(own);
		classes().that()
			.haveSimpleNameEndingWith("Service")
			.should()
			.resideInAPackage(base + ".application")
			.allowEmptyShould(true)
			.check(own);
		classes().that()
			.haveSimpleNameEndingWith("Listener")
			.should()
			.resideInAPackage(base + ".application")
			.allowEmptyShould(true)
			.check(own);
		classes().that()
			.haveSimpleNameEndingWith("Request")
			.or()
			.haveSimpleNameEndingWith("Response")
			.should()
			.resideInAPackage(base + ".web..")
			.allowEmptyShould(true)
			.check(own);

		// only the api package is visible to other modules
		noClasses().that()
			.resideOutsideOfPackages(base + "..")
			.and()
			.resideInAPackage(ROOT + "..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage(base + ".domain..", base + ".application..", base + ".persistence..", base + ".web..")
			.allowEmptyShould(true)
			.check(MAIN);

		// vocabulary: words we do not use (docs/conventions/01, section 3.5)
		noClasses().that()
			.resideInAPackage(base + "..")
			.should()
			.haveSimpleNameEndingWith("Dto")
			.orShould()
			.haveSimpleNameEndingWith("Manager")
			.orShould()
			.haveSimpleNameEndingWith("Util")
			.orShould()
			.haveSimpleNameEndingWith("Helper")
			.orShould()
			.haveSimpleName("MemberId")
			.allowEmptyShould(true)
			.check(own);
	}

	/**
	 * Events live in the publisher's api, with one exception (D12): the two balance
	 * events that flow upstream from settlement to group live in shared.events. A third
	 * class there would be a new upstream event, which is a design decision, not a
	 * convenience.
	 */
	@Test
	void sharedEventsAreExactlyTheTwoBalanceEvents() {
		List<JavaClass> shared = MAIN.stream()
			.filter(c -> c.getPackageName().equals(ROOT + ".shared.events") && !c.isNestedClass())
			.toList();

		assertThat(shared).allSatisfy(c -> assertThat(c.isRecord()).as(c.getName() + " is a record").isTrue());
		assertThat(shared).extracting(JavaClass::getSimpleName)
			.containsExactlyInAnyOrder("MemberBalanceStatusChanged", "AllBalancesSettled");
	}

	private static ArchCondition<JavaClass> beInterfaceRecordOrEnum() {
		return new ArchCondition<>("be an interface, a record or an enumeration") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				if (item.isNestedClass()) {
					return;
				}
				boolean ok = item.isInterface() || item.isRecord() || item.isEnum();
				events.add(new SimpleConditionEvent(item, ok, item.getName() + " is a plain class in api"));
			}
		};
	}

}
