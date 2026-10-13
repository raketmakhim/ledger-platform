package com.raketmakhim.ledger;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.raketmakhim.ledger")
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainIsFrameworkFree = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
			.allowEmptyShould(true);

}
