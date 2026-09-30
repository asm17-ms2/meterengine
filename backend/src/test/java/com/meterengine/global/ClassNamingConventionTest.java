package com.meterengine.global;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.meterengine", importOptions = ImportOption.DoNotIncludeTests.class)
class ClassNamingConventionTest {
  @ArchTest
  static final ArchRule 패키지는_도메인_아래_계층이거나_global_아래_역할이고_루트에는_진입점만_둔다 =
      classes()
          .that()
          .areTopLevelClasses()
          .should()
          .haveFullyQualifiedName("com.meterengine.MeterEngineApplication")
          .orShould()
          .resideInAnyPackage(
              "com.meterengine.*.client",
              "com.meterengine.*.config",
              "com.meterengine.*.controller",
              "com.meterengine.*.dto",
              "com.meterengine.*.entity",
              "com.meterengine.*.repository",
              "com.meterengine.*.service",
              "com.meterengine.global.*");

  @ArchTest
  static final ArchRule 복합키_클래스는_엔티티_이름_뒤에_Id를_붙인다 =
      classes().that().areAnnotatedWith(IdClass.class).should(haveIdClassNamedAfterEntity());

  @ArchTest
  static final ArchRule 리포지토리는_Repository로_끝난다 =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .or()
          .areAnnotatedWith(org.springframework.stereotype.Repository.class)
          .should()
          .haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule 서비스는_Service로_끝난다 =
      classes().that().areAnnotatedWith(Service.class).should().haveSimpleNameEndingWith("Service");

  @ArchTest
  static final ArchRule 컨트롤러는_Controller로_끝난다 =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .haveSimpleNameEndingWith("Controller");

  @ArchTest
  static final ArchRule 설정은_Config로_끝난다 =
      classes()
          .that()
          .areAnnotatedWith(Configuration.class)
          .should()
          .haveSimpleNameEndingWith("Config");

  @ArchTest
  static final ArchRule 외부_설정값은_Properties로_끝난다 =
      classes()
          .that()
          .areAnnotatedWith(ConfigurationProperties.class)
          .should()
          .haveSimpleNameEndingWith("Properties");

  @ArchTest
  static final ArchRule 쓰지_않는_접미사를_붙이지_않는다 =
      classes()
          .that()
          .areNotAnnotations()
          .should()
          .haveNameNotMatching(
              ".*(Dto|Vo|Cmd|Json|Model|Entity|Impl|Util|Helper|Manager|Entry|Item|Row|Data|Info)$");

  @ArchTest
  static final ArchRule 엔티티는_Table_없이_이름이_곧_테이블명이다 =
      classes().that().areAnnotatedWith(Entity.class).should().notBeAnnotatedWith(Table.class);

  @ArchTest
  static final ArchRule 예외는_Exception으로_끝난다 =
      classes()
          .that()
          .areAssignableTo(Throwable.class)
          .should()
          .haveSimpleNameEndingWith("Exception");

  private static ArchCondition<JavaClass> haveIdClassNamedAfterEntity() {
    return new ArchCondition<>("have @IdClass named <entity>Id") {
      @Override
      public void check(JavaClass entity, ConditionEvents events) {
        String idClassName = entity.getAnnotationOfType(IdClass.class).value().getSimpleName();
        String expected = entity.getSimpleName() + "Id";
        events.add(
            new SimpleConditionEvent(
                entity,
                idClassName.equals(expected),
                entity.getName() + " has @IdClass " + idClassName + ", expected " + expected));
      }
    };
  }
}
