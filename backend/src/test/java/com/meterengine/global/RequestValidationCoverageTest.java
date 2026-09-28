package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.StorableTimestamp;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestValidationCoverageTest {

  private static final String BASE_PACKAGE = "com.meterengine";

  private static final Set<Class<?>> KNOWN_SCALAR_TYPES =
      Set.of(String.class, Map.class, OffsetDateTime.class, UUID.class);

  @Test
  void 요청_DTO를_빠짐없이_찾는다() {
    assertThat(requestRecords())
        .extracting(Class::getSimpleName)
        .contains(
            "CreateCustomerRequest",
            "UpdateCustomerRequest",
            "CreateBillableMetricRequest",
            "UpdateBillableMetricRequest",
            "CreatePricePolicyRequest",
            "IngestEventRequest");
  }

  @Test
  void 컨트롤러를_빠짐없이_찾는다() {
    assertThat(controllers())
        .extracting(Class::getSimpleName)
        .contains("CustomerController", "EventController", "PricePolicyController");
  }

  @Test
  void 요청_DTO의_문자열에는_모두_StorableText가_붙어_있다() {
    List<String> unguardedComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(component -> carriesText(component) && !guardsText(component))
            .map(RequestValidationCoverageTest::describe)
            .toList();

    assertThat(unguardedComponents).isEmpty();
  }

  @Test
  void 컨트롤러의_문자열_파라미터에는_모두_StorableText가_붙어_있다() {
    List<String> unguardedParameters =
        controllers().stream()
            .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
            .filter(RequestValidationCoverageTest::isHandler)
            .flatMap(
                method ->
                    Stream.of(method.getParameters())
                        .filter(parameter -> !parameter.isAnnotationPresent(RequestBody.class))
                        .filter(parameter -> carriesText(parameter) && !guardsText(parameter))
                        .map(
                            parameter ->
                                method.getDeclaringClass().getSimpleName()
                                    + "."
                                    + method.getName()
                                    + "("
                                    + parameter.getName()
                                    + ")"))
            .toList();

    assertThat(unguardedParameters).isEmpty();
  }

  @Test
  void 요청_DTO의_JSON_객체와_시각에는_저장_가능_제약이_붙어_있다() {
    List<String> unguardedComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(
                component ->
                    (component.getType() == Map.class
                            && !backingField(component).isAnnotationPresent(StorableJson.class))
                        || (component.getType() == OffsetDateTime.class
                            && !backingField(component)
                                .isAnnotationPresent(StorableTimestamp.class)))
            .map(RequestValidationCoverageTest::describe)
            .toList();

    assertThat(unguardedComponents).isEmpty();
  }

  @Test
  void 요청_DTO의_필드는_이_가드가_검사할_줄_아는_타입뿐이다() {
    List<String> unknownTypeComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(component -> !isKnownType(component))
            .map(component -> describe(component) + ": " + component.getGenericType())
            .toList();

    assertThat(unknownTypeComponents).isEmpty();
  }

  static List<Class<?>> requestRecords() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        new RegexPatternTypeFilter(Pattern.compile(".*\\.dto\\.[A-Za-z]+Request")));
    return classes(scanner).stream().filter(Class::isRecord).toList();
  }

  static List<Class<?>> controllers() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    return classes(scanner).stream().filter(type -> type.getEnclosingClass() == null).toList();
  }

  static Field backingField(RecordComponent component) {
    try {
      return component.getDeclaringRecord().getDeclaredField(component.getName());
    } catch (NoSuchFieldException exception) {
      throw new IllegalStateException(describe(component), exception);
    }
  }

  static String describe(RecordComponent component) {
    return component.getDeclaringRecord().getSimpleName() + "." + component.getName();
  }

  private static List<Class<?>> classes(ClassPathScanningCandidateComponentProvider scanner) {
    return scanner.findCandidateComponents(BASE_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(
            name ->
                ClassUtils.resolveClassName(
                    name, RequestValidationCoverageTest.class.getClassLoader()))
        .toList();
  }

  private static boolean carriesText(RecordComponent component) {
    return component.getType() == String.class || isListOfString(component);
  }

  private static boolean guardsText(RecordComponent component) {
    Field field = backingField(component);
    if (component.getType() == String.class) {
      return field.isAnnotationPresent(StorableText.class);
    }
    return field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterized
        && parameterized.getAnnotatedActualTypeArguments()[0].isAnnotationPresent(
            StorableText.class);
  }

  private static boolean isListOfString(RecordComponent component) {
    return component.getType() == List.class
        && component.getGenericType() instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments()[0] == String.class;
  }

  private static boolean isKnownType(RecordComponent component) {
    if (component.getType() == List.class) {
      return isListOfString(component);
    }
    return KNOWN_SCALAR_TYPES.contains(component.getType());
  }

  private static boolean isHandler(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
  }

  private static boolean carriesText(Parameter parameter) {
    return parameter.getType() == String.class
        || (parameter.getType() == List.class
            && parameter.getParameterizedType() instanceof ParameterizedType parameterized
            && parameterized.getActualTypeArguments()[0] == String.class);
  }

  private static boolean guardsText(Parameter parameter) {
    if (parameter.getType() == String.class) {
      return parameter.isAnnotationPresent(StorableText.class);
    }
    return parameter.getAnnotatedType() instanceof AnnotatedParameterizedType parameterized
        && parameterized.getAnnotatedActualTypeArguments()[0].isAnnotationPresent(
            StorableText.class);
  }
}
