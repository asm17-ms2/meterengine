package com.meterengine.global;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.platform.commons.annotation.Testable;

@AnalyzeClasses(packages = "com.meterengine", importOptions = ImportOption.OnlyIncludeTests.class)
class TestNamingConventionTest {

  private static final DescribedPredicate<JavaMethod> IS_TEST_METHOD =
      new DescribedPredicate<>("is a test method") {
        @Override
        public boolean test(JavaMethod method) {
          return method.isMetaAnnotatedWith(Testable.class);
        }
      };

  private static final DescribedPredicate<JavaClass> HAVE_TEST_METHODS =
      new DescribedPredicate<>("have test methods") {
        @Override
        public boolean test(JavaClass javaClass) {
          return javaClass.getMethods().stream().anyMatch(IS_TEST_METHOD)
              || javaClass.getFields().stream()
                  .anyMatch(field -> field.isAnnotatedWith(ArchTest.class));
        }
      };

  @ArchTest
  static final ArchRule 테스트_클래스는_Test로_끝난다 =
      classes()
          .that(HAVE_TEST_METHODS)
          .and()
          .areNotAnnotatedWith(Nested.class)
          .should()
          .haveSimpleNameEndingWith("Test");

  @ArchTest
  static final ArchRule 테스트_메서드는_한국어_문장이다 =
      methods()
          .that(IS_TEST_METHOD)
          .should()
          .haveNameMatching("^[가-힣0-9A-Za-z_]*[가-힣][가-힣0-9A-Za-z_]*다$");

  @ArchTest
  static final ArchRule 테스트가_아닌_메서드는_영문이다 =
      methods()
          .that(DescribedPredicate.not(IS_TEST_METHOD))
          .and()
          .doNotHaveModifier(JavaModifier.SYNTHETIC)
          .and()
          .areDeclaredInClassesThat(HAVE_TEST_METHODS)
          .should()
          .haveNameMatching("^[a-z][a-zA-Z0-9]*$");

  @ArchTest
  static final ArchRule DisplayName을_쓰지_않는다 =
      noMembers()
          .should()
          .beAnnotatedWith(DisplayName.class)
          .orShould()
          .beDeclaredInClassesThat()
          .areAnnotatedWith(DisplayName.class);

  @ArchTest
  static final ArchRule 루트에는_애플리케이션_테스트만_둔다 =
      classes()
          .that(HAVE_TEST_METHODS)
          .and()
          .resideInAPackage("com.meterengine")
          .should()
          .haveSimpleName("MeterEngineApplicationTest");

  @ArchTest
  static void 클래스_테스트는_대상과_같은_패키지에_관점_테스트는_도메인_루트에_둔다(JavaClasses tests) {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.meterengine");
    Map<String, Set<String>> simpleNamesByPackage =
        production.stream()
            .filter(JavaClass::isTopLevelClass)
            .collect(
                Collectors.groupingBy(
                    JavaClass::getPackageName,
                    Collectors.mapping(JavaClass::getSimpleName, Collectors.toSet())));
    classes()
        .that(HAVE_TEST_METHODS)
        .and()
        .areTopLevelClasses()
        .and()
        .resideOutsideOfPackage("com.meterengine")
        .should(resideWithTheirTargetOrInADomainRoot(simpleNamesByPackage))
        .check(tests);
  }

  private static ArchCondition<JavaClass> resideWithTheirTargetOrInADomainRoot(
      Map<String, Set<String>> simpleNamesByPackage) {
    return new ArchCondition<>("reside with the class they test, or in a domain root or global") {
      @Override
      public void check(JavaClass test, ConditionEvents events) {
        boolean inDomainRoot = test.getPackageName().matches("com\\.meterengine\\.[a-z]+");
        boolean withTarget =
            simpleNamesByPackage.getOrDefault(test.getPackageName(), Set.of()).stream()
                .anyMatch(name -> test.getSimpleName().startsWith(name));
        events.add(
            new SimpleConditionEvent(
                test,
                inDomainRoot || withTarget,
                test.getName() + " does not reside with its target class or in a domain root"));
      }
    };
  }
}
