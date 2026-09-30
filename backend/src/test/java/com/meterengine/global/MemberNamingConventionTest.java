package com.meterengine.global;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.meterengine", importOptions = ImportOption.DoNotIncludeTests.class)
class MemberNamingConventionTest {
  private static final String ROOT_PACKAGE = "com.meterengine.";

  @ArchTest
  static final ArchRule 서비스_메서드는_한_단어다 =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Service.class)
          .and()
          .arePublic()
          .and()
          .doNotHaveModifier(JavaModifier.STATIC)
          .and()
          .doNotHaveRawReturnType(boolean.class)
          .and()
          .doNotHaveRawReturnType(Boolean.class)
          .should()
          .haveNameMatching("^[a-z]+$");

  @ArchTest
  static void 컨트롤러_메서드는_서비스_메서드_낱말_뒤에_리소스를_붙인다(JavaClasses classes) {
    Map<String, Set<String>> serviceVerbsByDomain =
        classes.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(Service.class))
            .collect(
                Collectors.groupingBy(
                    MemberNamingConventionTest::domainOf,
                    Collectors.flatMapping(
                        javaClass ->
                            javaClass.getMethods().stream()
                                .map(JavaMethod::getName)
                                .filter(name -> name.matches("^[a-z]+$")),
                        Collectors.toSet())));
    methods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(RestController.class)
        .and()
        .arePublic()
        .should(startWithOwnDomainServiceVerbThenResource(serviceVerbsByDomain))
        .check(classes);
  }

  @ArchTest
  static final ArchRule 파생_쿼리는_find_exists_count_delete_뒤에_By를_붙인다 =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAssignableTo(Repository.class)
          .and()
          .haveModifier(JavaModifier.ABSTRACT)
          .and()
          .areNotAnnotatedWith(Query.class)
          .should()
          .haveNameMatching("^(find|exists|count|delete)By[A-Z].*");

  @ArchTest
  static final ArchRule 엔티티의_boolean_접근자는_is로_시작한다 =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Entity.class)
          .and()
          .arePublic()
          .and()
          .haveRawReturnType(boolean.class)
          .and()
          .haveRawParameterTypes(new Class<?>[0])
          .should()
          .haveNameMatching("^is[A-Z].*");

  @ArchTest
  static final ArchRule boolean_필드는_접두사가_없다 =
      fields()
          .that()
          .haveRawType(boolean.class)
          .or()
          .haveRawType(Boolean.class)
          .should()
          .haveNameNotMatching("^(is|has|can)[A-Z].*")
          .orShould()
          .haveName("isNew");

  @ArchTest
  static final ArchRule 순간_필드는_At로_끝난다 =
      fields()
          .that()
          .haveRawType(Instant.class)
          .or()
          .haveRawType(OffsetDateTime.class)
          .and()
          .doNotHaveModifier(JavaModifier.STATIC)
          .should()
          .haveNameMatching(".*At$");

  @ArchTest
  static final ArchRule 날짜_필드는_Date로_끝난다 =
      fields()
          .that()
          .haveRawType(LocalDate.class)
          .and()
          .doNotHaveModifier(JavaModifier.STATIC)
          .should()
          .haveNameMatching(".*Date$")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule 컬렉션_필드는_타입_접미사가_없다 =
      fields()
          .that()
          .haveRawType(assignableTo(Collection.class))
          .should()
          .haveNameNotMatching(".*(List|Set|Collection)$");

  @ArchTest
  static final ArchRule dto_밖의_Map_필드는_값_By_키다 =
      fields()
          .that()
          .haveRawType(assignableTo(Map.class))
          .and()
          .doNotHaveModifier(JavaModifier.STATIC)
          .and()
          .areDeclaredInClassesThat()
          .resideOutsideOfPackage("..dto..")
          .should()
          .haveNameMatching(".+By[A-Z].*")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule 주입_필드는_타입명_그대로다 =
      fields()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Service.class)
          .or()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .or()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(org.springframework.stereotype.Repository.class)
          .and()
          .haveModifier(JavaModifier.FINAL)
          .and()
          .doNotHaveModifier(JavaModifier.STATIC)
          .and()
          .haveRawType(resideOutsideOfPackage("java.."))
          .should(beNamedAfterTheirType());

  @ArchTest
  static final ArchRule 불변_값_상수는_UPPER_SNAKE_CASE다 =
      fields()
          .that()
          .haveModifier(JavaModifier.STATIC)
          .and()
          .haveModifier(JavaModifier.FINAL)
          .and(haveTypeThatIsImmutableValue())
          .should()
          .haveNameMatching("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

  @ArchTest
  static final ArchRule 핸들러_메서드는_handle_뒤에_예외_클래스_이름이다 =
      methods()
          .that()
          .areAnnotatedWith(ExceptionHandler.class)
          .should(beNamedHandleAndTheException());

  private static ArchCondition<JavaField> beNamedAfterTheirType() {
    return new ArchCondition<>("be named after their type") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        String typeName = field.getRawType().getSimpleName();
        String expected = Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
        long sameTypeCount =
            field.getOwner().getFields().stream()
                .filter(other -> other.getRawType().equals(field.getRawType()))
                .count();
        boolean satisfied =
            sameTypeCount > 1
                ? field.getName().endsWith(typeName)
                : field.getName().equals(expected);
        events.add(
            new SimpleConditionEvent(
                field, satisfied, field.getFullName() + " should be named " + expected));
      }
    };
  }

  private static ArchCondition<JavaMethod> startWithOwnDomainServiceVerbThenResource(
      Map<String, Set<String>> serviceVerbsByDomain) {
    return new ArchCondition<>("start with a same-domain service method name then a resource") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        String name = method.getName();
        Set<String> verbs =
            serviceVerbsByDomain.getOrDefault(domainOf(method.getOwner()), Set.of());
        boolean satisfied =
            verbs.stream()
                .anyMatch(
                    verb ->
                        name.startsWith(verb)
                            && name.substring(verb.length()).matches("^[A-Z][A-Za-z]*$"));
        events.add(
            new SimpleConditionEvent(
                method,
                satisfied,
                method.getFullName() + " should start with one of " + verbs + " then a resource"));
      }
    };
  }

  private static String domainOf(JavaClass javaClass) {
    return javaClass.getPackageName().substring(ROOT_PACKAGE.length()).split("\\.")[0];
  }

  private static ArchCondition<JavaMethod> beNamedHandleAndTheException() {
    return new ArchCondition<>("be named handle<exception class>") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        String expected = "handle" + method.getRawParameterTypes().get(0).getSimpleName();
        events.add(
            new SimpleConditionEvent(
                method,
                method.getName().equals(expected),
                method.getFullName() + " should be named " + expected));
      }
    };
  }

  private static DescribedPredicate<JavaField> haveTypeThatIsImmutableValue() {
    return new DescribedPredicate<>(
        "have a primitive, String, enum, java.time, BigDecimal or UUID type") {
      @Override
      public boolean test(JavaField field) {
        JavaClass type = field.getRawType();
        return type.isPrimitive()
            || type.isEnum()
            || type.isEquivalentTo(String.class)
            || type.isEquivalentTo(BigDecimal.class)
            || type.isEquivalentTo(UUID.class)
            || type.getPackageName().startsWith("java.time");
      }
    };
  }
}
