package com.meterengine.global;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.IngestAccess;
import com.meterengine.global.security.MemberAccess;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.security.OperatorAccess;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.security.ReadAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.meterengine", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiAccessCoverageTest {

  private static final List<Class<? extends Annotation>> ACCESS_ANNOTATIONS =
      List.of(
          IngestAccess.class,
          ReadAccess.class,
          AdminAccess.class,
          MemberAccess.class,
          MemberReadAccess.class,
          MemberAdminAccess.class,
          OperatorAccess.class,
          PublicAccess.class);

  @ArchTest
  static final ArchRule 핸들러에는_접근_어노테이션이_정확히_하나_붙어_있다 =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .and()
          .areMetaAnnotatedWith(RequestMapping.class)
          .should(haveExactlyOneAccessAnnotation());

  private static ArchCondition<JavaMethod> haveExactlyOneAccessAnnotation() {
    return new ArchCondition<>("have exactly one access annotation") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        List<String> present =
            ACCESS_ANNOTATIONS.stream()
                .filter(method::isAnnotatedWith)
                .map(Class::getSimpleName)
                .toList();
        events.add(
            new SimpleConditionEvent(
                method,
                present.size() == 1,
                method.getFullName() + " has access annotations " + present));
      }
    };
  }
}
