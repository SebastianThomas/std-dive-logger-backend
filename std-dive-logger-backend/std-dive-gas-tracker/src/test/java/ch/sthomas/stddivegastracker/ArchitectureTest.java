package ch.sthomas.stddivegastracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layering from AGENTS.md "Package layering" - this test is the enforcement, not the doc. */
@AnalyzeClasses(
        packages = ArchitectureTest.BASE,
        importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    static final String BASE = "ch.sthomas.stddivegastracker";

    @ArchTest
    static final ArchRule calcIsPureJava =
            classes()
                    .that()
                    .resideInAPackage(BASE + ".calc..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "org.jspecify..", BASE + ".calc..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modelDependsOnNoUpperLayer =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + ".model..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BASE + ".data..", BASE + ".service..", BASE + ".ws..");

    @ArchTest
    static final ArchRule dataDependsOnNeitherServiceNorWs =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + ".data..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BASE + ".service..", BASE + ".ws..");

    @ArchTest
    static final ArchRule serviceDoesNotDependOnWs =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + ".service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(BASE + ".ws..");

    @ArchTest
    static final ArchRule wsUsesNeitherRepositoriesNorEntities =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + ".ws..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BASE + ".data.repository..", BASE + ".model.entity..");

    @ArchTest
    static final ArchRule noDiveLoggerCode =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("ch.sthomas.stddivelogger..");

    @ArchTest
    static final ArchRule layersAreAcyclic =
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles();
}
