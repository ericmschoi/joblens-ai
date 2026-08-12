package com.joblens;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Structural rules that keep the boundaries described in the architecture plan enforceable rather
 * than aspirational. Rules for later phases (for example "scoring must not see opportunity value")
 * are added together with the packages they constrain.
 */
@AnalyzeClasses(packages = "com.joblens", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnTheApiLayer = noClasses()
            .that().resideInAnyPackage("com.joblens.analysis..", "com.joblens.config..",
                    "com.joblens.resume..", "com.joblens.document..")
            .should().dependOnClassesThat().resideInAPackage("com.joblens.api..")
            .because("the API layer adapts the domain, never the other way around");

    @ArchTest
    static final ArchRule noFieldInjection = fields()
            .should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("constructor injection keeps collaborators explicit and testable");

    /**
     * Fit and opportunity are different questions, and the scoring engine must not be able to
     * confuse them. A prestigious employer or a generous salary cannot reach the arithmetic.
     */
    @ArchTest
    static final ArchRule scoringCannotSeeOpportunityValue = noClasses()
            .that().resideInAPackage("com.joblens.scoring")
            .and().haveSimpleNameEndingWith("Calculator")
            .or().haveSimpleNameEndingWith("Scorer")
            .or().haveSimpleNameEndingWith("Policy")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.joblens.analysis.model.OpportunityValue")
            .because("how attractive a job is has nothing to do with whether this candidate fits it");

    @ArchTest
    static final ArchRule noConsoleOutput = noClasses()
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("all diagnostics go through SLF4J, which is where the no-document-content "
                    + "logging rule is enforced");
}
