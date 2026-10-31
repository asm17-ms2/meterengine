package com.meterengine.global;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
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
              "com.meterengine.*.api",
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
          .haveSimpleNameEndingWith("Properties")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule 쓰지_않는_접미사를_붙이지_않는다 =
      classes()
          .that()
          .areNotAnnotations()
          .should()
          .haveNameNotMatching(
              ".*(Dto|Vo|Cmd|Json|Model|Entity|Impl|Util|Helper|Manager|Entry|Item|Row|Data|Info)$");

  @ArchTest
  static final ArchRule 엔티티는_이름이_곧_테이블명이고_공통_schema만_명시한다 =
      classes().that().areAnnotatedWith(Entity.class).should(useOnlySharedSchema());

  private static ArchCondition<JavaClass> useOnlySharedSchema() {
    var sharedEntities =
        java.util.Set.of(
            "com.meterengine.account.entity.UserAccount",
            "com.meterengine.account.entity.UserSession",
            "com.meterengine.account.entity.Consent",
            "com.meterengine.organization.entity.Organization",
            "com.meterengine.organization.entity.OrganizationMember",
            "com.meterengine.organization.entity.OrganizationInvitation",
            "com.meterengine.apikey.entity.ApiKey",
            "com.meterengine.audit.entity.AuditLog",
            "com.meterengine.scheduling.entity.ScheduledJobRun");
    return new ArchCondition<>("use only schema=shared on approved shared entities") {
      @Override
      public void check(JavaClass entity, ConditionEvents events) {
        boolean shared = sharedEntities.contains(entity.getName());
        Table table =
            entity.isAnnotatedWith(Table.class) ? entity.getAnnotationOfType(Table.class) : null;
        boolean valid = shared ? hasOnlySharedSchema(table) : table == null;
        events.add(
            new SimpleConditionEvent(
                entity,
                valid,
                entity.getName() + " must retain its implicit table name and approved schema"));
      }
    };
  }

  private static boolean hasOnlySharedSchema(Table table) {
    if (table == null || !table.schema().equals("shared")) return false;
    try {
      for (var member : Table.class.getDeclaredMethods()) {
        if (!member.getName().equals("schema")
            && !java.util.Objects.deepEquals(member.invoke(table), member.getDefaultValue()))
          return false;
      }
      return true;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot inspect @Table defaults", exception);
    }
  }

  @Test
  void shared_schema만_지정한_Table은_허용한다() {
    assertThat(hasOnlySharedSchema(SharedTable.class.getAnnotation(Table.class))).isTrue();
  }

  @Test
  void Table의_indexes_추가는_거절한다() {
    assertRejected(IndexedTable.class);
  }

  @Test
  void Table의_uniqueConstraints_추가는_거절한다() {
    assertRejected(UniqueTable.class);
  }

  @Test
  void Table의_check_추가는_거절한다() {
    assertRejected(CheckedTable.class);
  }

  @Test
  void Table의_comment_추가는_거절한다() {
    assertRejected(CommentedTable.class);
  }

  @Test
  void Table의_options_추가는_거절한다() {
    assertRejected(OptionsTable.class);
  }

  @Test
  void Table의_name_변경은_거절한다() {
    assertRejected(NamedTable.class);
  }

  @Test
  void Table의_catalog_변경은_거절한다() {
    assertRejected(CatalogTable.class);
  }

  @Test
  void Table의_shared가_아닌_schema는_거절한다() {
    assertRejected(BusinessTable.class);
  }

  private static void assertRejected(Class<?> fixture) {
    assertThat(hasOnlySharedSchema(fixture.getAnnotation(Table.class))).isFalse();
  }

  @Table(schema = "shared")
  private static class SharedTable {}

  @Table(schema = "shared", indexes = @Index(columnList = "id"))
  private static class IndexedTable {}

  @Table(schema = "shared", uniqueConstraints = @UniqueConstraint(columnNames = "id"))
  private static class UniqueTable {}

  @Table(schema = "shared", check = @CheckConstraint(constraint = "id is not null"))
  private static class CheckedTable {}

  @Table(schema = "shared", comment = "not allowed")
  private static class CommentedTable {}

  @Table(schema = "shared", options = "not allowed")
  private static class OptionsTable {}

  @Table(schema = "shared", name = "not_allowed")
  private static class NamedTable {}

  @Table(schema = "shared", catalog = "not_allowed")
  private static class CatalogTable {}

  @Table(schema = "test")
  private static class BusinessTable {}

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
