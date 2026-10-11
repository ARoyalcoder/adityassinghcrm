package com.pawanputra.bos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the modular-monolith rules described in docs/architecture.md. If one of these fails,
 * fix the dependency rather than the rule.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "com.pawanputra.bos";
    private static final String PLATFORM = ROOT + ".platform";

    @ArchTest
    static final ArchRule platformDoesNotDependOnFeatureModules = noClasses()
            .that().resideInAPackage(PLATFORM + "..")
            .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ROOT + "..")
                    .and(JavaClass.Predicates.resideOutsideOfPackage(PLATFORM + "..")))
            .because("platform is the shared kernel and must stay reusable by every module");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule moduleInternalsAreNotUsedByOtherModules = classes()
            .that().resideInAnyPackage(ROOT + ".*.internal..", ROOT + ".*.web..")
            .and().resideOutsideOfPackage(PLATFORM + "..")
            .should(onlyBeUsedWithinTheirOwnModule())
            .because("other modules may only use a module's 'api' package or its domain events");

    @ArchTest
    static final ArchRule controllersLiveInWebPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule entitiesAreModuleInternal = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..internal..")
            .because("JPA entities must never cross a module boundary or reach the API");

    private static ArchCondition<JavaClass> onlyBeUsedWithinTheirOwnModule() {
        return new ArchCondition<>("only be used within their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String owner = moduleOf(target);
                for (Dependency dependency : target.getDirectDependenciesToSelf()) {
                    String user = moduleOf(dependency.getOriginClass());
                    if (!owner.equals(user)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** {@code com.pawanputra.bos.catalog.internal.Foo -> "catalog"}; the root package maps to "". */
    private static String moduleOf(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            return "";
        }
        String rest = pkg.substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
