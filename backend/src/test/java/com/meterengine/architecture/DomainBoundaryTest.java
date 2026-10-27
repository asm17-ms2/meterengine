package com.meterengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.meterengine", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainBoundaryTest {
  @ArchTest
  static final ArchRule domains_have_no_cycles =
      slices().matching("com.meterengine.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule ingestion_uses_public_customer_contracts =
      noClasses()
          .that()
          .resideInAPackage("com.meterengine.event..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.meterengine.customer.repository..", "com.meterengine.customer.entity..",
              "com.meterengine.customer.service..", "com.meterengine.metric..");

  @ArchTest
  static final ArchRule customer_does_not_reach_into_usage_or_payments =
      noClasses()
          .that()
          .resideInAPackage("com.meterengine.customer..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.meterengine.event..", "com.meterengine.payment..", "com.meterengine.pricing..");

  @ArchTest
  static final ArchRule global_depends_only_on_global =
      noClasses()
          .that()
          .resideInAPackage("com.meterengine.global..")
          .should()
          .dependOnClassesThat(
              com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                      "com.meterengine..")
                  .and(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage(
                          "com.meterengine.global..")));

  @ArchTest
  static final ArchRule organization_does_not_import_accounts =
      noClasses()
          .that()
          .resideInAPackage("com.meterengine.organization..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.meterengine.account..");
}
