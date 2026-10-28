package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableJsonValidator;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.StorableTimestamp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.ClassUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

class RequestValidationCoverageTest {

  private static final String BASE_PACKAGE = "com.meterengine";

  private static final Set<Class<?>> KNOWN_SCALAR_TYPES =
      Set.of(
          String.class,
          Map.class,
          OffsetDateTime.class,
          UUID.class,
          BigDecimal.class,
          Long.class,
          Integer.class,
          Boolean.class,
          YearMonth.class);

  private static final Set<Class<?>> KNOWN_PARAMETER_SCALAR_TYPES =
      Set.of(
          String.class,
          YearMonth.class,
          LocalDate.class,
          UUID.class,
          int.class,
          Integer.class,
          long.class,
          Long.class,
          boolean.class,
          Boolean.class);

  @Test
  void 요청_DTO를_빠짐없이_찾는다() {
    assertThat(requestRecords())
        .extracting(Class::getSimpleName)
        .contains(
            "CreateCustomerRequest",
            "UpdateCustomerRequest",
            "CreateBillableMetricRequest",
            "UpdateBillableMetricRequest",
            "CreatePriceRateRequest",
            "IngestEventRequest",
            "CreateSignupRequest",
            "CreateSignupConsentRequest",
            "CreateApiKeyRequest",
            "CreatePaymentCancellationRequest",
            "IngestEventBatchRequest",
            "CreatePricePlanRequest",
            "CreateWebhookEndpointRequest");
  }

  @Test
  void 요청_레코드_목록의_원소_레코드도_요청_DTO로_검사된다() {
    List<Class<?>> requestRecords = requestRecords();
    List<String> uncheckedElements =
        requestRecords.stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(RequestValidationCoverageTest::isListOfRequestRecord)
            .filter(component -> !requestRecords.contains(listElementType(component)))
            .map(RequestValidationCoverageTest::describe)
            .toList();

    assertThat(uncheckedElements).isEmpty();
  }

  @Test
  void 요청_레코드_목록에는_NotEmpty와_Valid와_원소_NotNull이_붙어_있다() {
    List<String> unguardedComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(RequestValidationCoverageTest::isListOfRequestRecord)
            .filter(
                component ->
                    !(requiresNonEmpty(component)
                            || isPricingTiers(component)
                            || isInvitationConsents(component))
                        || !validatesElements(component)
                        || !forbidsNullElements(component))
            .map(RequestValidationCoverageTest::describe)
            .toList();

    assertThat(unguardedComponents).isEmpty();
  }

  @Test
  void 가입_신청의_동의_목록은_검사되는_요청_레코드_목록이다() {
    RecordComponent consents =
        Stream.of(requestRecord("CreateSignupRequest").getRecordComponents())
            .filter(component -> component.getName().equals("consents"))
            .findFirst()
            .orElseThrow();

    assertThat(isListOfRequestRecord(consents)).isTrue();
    assertThat(listElementType(consents).getSimpleName()).isEqualTo("CreateSignupConsentRequest");
    assertThat(backingField(consents).isAnnotationPresent(NotEmpty.class)).isTrue();
    assertThat(validatesElements(consents)).isTrue();
    assertThat(forbidsNullElements(consents)).isTrue();
  }

  @Test
  void 스코프_목록은_비어_있지_않고_원소가_null이_아닌_ingest_read_admin_가운데_하나다() {
    List<String> problems =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(component -> component.getName().equals("scopes"))
            .<String>mapMulti(
                (component, problem) -> {
                  if (!isListOfString(component)
                      || !requiresNonEmpty(component)
                      || !guardsText(component)
                      || !forbidsNullElements(component)
                      || !restrictsToScopes(component)) {
                    problem.accept(describe(component));
                  }
                })
            .toList();

    assertThat(problems).isEmpty();
  }

  @Test
  void 컨트롤러를_빠짐없이_찾는다() {
    assertThat(controllers())
        .extracting(Class::getSimpleName)
        .contains("CustomerController", "EventController", "BillableMetricController");
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
  void 요청_DTO의_JSON_객체와_시각과_소수에는_저장_가능_제약이_붙어_있다() {
    List<String> unguardedComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(
                component ->
                    (component.getType() == Map.class
                            && !backingField(component).isAnnotationPresent(StorableJson.class))
                        || (component.getType() == OffsetDateTime.class
                            && !backingField(component)
                                .isAnnotationPresent(StorableTimestamp.class))
                        || (component.getType() == BigDecimal.class && !guardsDecimal(component)))
            .map(RequestValidationCoverageTest::describe)
            .toList();

    assertThat(unguardedComponents).isEmpty();
  }

  @Test
  void 요청_DTO의_Long에는_양수나_최솟값_제약이_붙어_있다() {
    List<String> unguardedComponents =
        requestRecords().stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(
                component ->
                    (component.getType() == Long.class || component.getType() == Integer.class)
                        && !backingField(component).isAnnotationPresent(Positive.class)
                        && !backingField(component).isAnnotationPresent(Min.class))
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

  @Test
  void 컨트롤러의_파라미터는_이_가드가_검사할_줄_아는_타입뿐이고_YearMonth와_LocalDate에는_FourDigitYear가_붙어_있다() {
    List<String> problems =
        controllers().stream()
            .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
            .filter(RequestValidationCoverageTest::isHandler)
            .flatMap(
                method ->
                    Stream.of(method.getParameters())
                        .filter(parameter -> !parameter.isAnnotationPresent(RequestBody.class))
                        .<String>mapMulti(
                            (parameter, problem) -> {
                              if (!isKnownType(parameter)) {
                                problem.accept(
                                    describe(method, parameter)
                                        + ": "
                                        + parameter.getParameterizedType());
                              } else if ((parameter.getType() == YearMonth.class
                                      || parameter.getType() == LocalDate.class)
                                  && !parameter.isAnnotationPresent(FourDigitYear.class)) {
                                problem.accept(describe(method, parameter));
                              }
                            }))
            .toList();

    assertThat(problems).isEmpty();
  }

  @Test
  void 요청_본문_파라미터에는_Valid가_붙어_있다() {
    List<String> unguardedParameters =
        controllers().stream()
            .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
            .filter(RequestValidationCoverageTest::isHandler)
            .flatMap(
                method ->
                    Stream.of(method.getParameters())
                        .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                        .filter(
                            parameter ->
                                !parameter.isAnnotationPresent(Valid.class)
                                    && !parameter.isAnnotationPresent(Validated.class))
                        .map(parameter -> describe(method, parameter)))
            .toList();

    assertThat(unguardedParameters).isEmpty();
  }

  static List<Class<?>> requestRecords() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        new RegexPatternTypeFilter(Pattern.compile(".*\\.dto\\.[A-Za-z]+Request")));
    Set<Class<?>> records =
        new java.util.HashSet<>(classes(scanner).stream().filter(Class::isRecord).toList());
    List<Class<?>> nested =
        records.stream()
            .flatMap(record -> Stream.of(record.getRecordComponents()))
            .filter(RequestValidationCoverageTest::isListOfRequestRecord)
            .map(RequestValidationCoverageTest::listElementType)
            .toList();
    records.addAll(nested);
    return records.stream().toList();
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

  private static boolean guardsDecimal(RecordComponent component) {
    Digits digits = backingField(component).getAnnotation(Digits.class);
    return digits != null
        && digits.integer() == StorableJsonValidator.MAX_INTEGER_DIGITS
        && digits.fraction() == StorableJsonValidator.MAX_FRACTION_DIGITS;
  }

  private static boolean isListOfString(RecordComponent component) {
    return component.getType() == List.class
        && component.getGenericType() instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments()[0] == String.class;
  }

  private static boolean isKnownType(RecordComponent component) {
    if (component.getType() == List.class) {
      return isListOfString(component)
          || isListOfRequestRecord(component)
          || isBatchEvents(component);
    }
    return KNOWN_SCALAR_TYPES.contains(component.getType());
  }

  private static boolean isListOfRequestRecord(RecordComponent component) {
    if (component.getType() != List.class
        || !(component.getGenericType() instanceof ParameterizedType parameterized)
        || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> element)) {
      return false;
    }
    return element.isRecord()
        && element.getPackageName().equals(component.getDeclaringRecord().getPackageName());
  }

  private static boolean isBatchEvents(RecordComponent component) {
    return component.getDeclaringRecord().getSimpleName().equals("IngestEventBatchRequest")
        && component.getName().equals("events")
        && component.getType() == List.class
        && listElementType(component) == JsonNode.class;
  }

  private static boolean isPricingTiers(RecordComponent component) {
    return component.getDeclaringRecord().getSimpleName().equals("CreatePriceRateRequest")
        && component.getName().equals("tiers");
  }

  // 새 계정 분기에서 서비스가 빈 동의를 거절한다. 짝 시험은 OrganizationPeopleIntegrationTest에 있다.
  private static boolean isInvitationConsents(RecordComponent component) {
    return component
            .getDeclaringRecord()
            .getSimpleName()
            .equals("AcceptOrganizationInvitationRequest")
        && component.getName().equals("consents");
  }

  private static boolean requiresNonEmpty(RecordComponent component) {
    return Stream.of(backingField(component).getAnnotationsByType(NotEmpty.class))
        .anyMatch(
            notEmpty ->
                notEmpty.groups().length == 0
                    || List.of(notEmpty.groups()).contains(Default.class));
  }

  @Test
  void 배치_봉투와_조건부_요금_구간의_제약을_검사한다() throws Exception {
    Field events = requestRecord("IngestEventBatchRequest").getDeclaredField("events");
    assertThat(events.isAnnotationPresent(NotEmpty.class)).isTrue();
    assertThat(events.getAnnotation(Size.class).max()).isEqualTo(100);
    Field tiers = requestRecord("CreatePriceRateRequest").getDeclaredField("tiers");
    assertThat(tiers.getAnnotation(Size.class).min()).isEqualTo(1);
    assertThat(tiers.getAnnotation(Size.class).max()).isEqualTo(100);
    assertThat(
            requestRecord("CreatePriceRateRequest")
                .getMethod("isValidPricing")
                .isAnnotationPresent(AssertTrue.class))
        .isTrue();
  }

  @Test
  void 요청_DTO의_월과_참거짓과_금액_타입의_제약을_검사한다() {
    for (Class<?> record : requestRecords()) {
      for (RecordComponent component : record.getRecordComponents()) {
        Field field = backingField(component);
        if (component.getType() == YearMonth.class)
          assertThat(field.isAnnotationPresent(FourDigitYear.class))
              .as(describe(component))
              .isTrue();
        if (component.getType() == Boolean.class)
          assertThat(field.isAnnotationPresent(NotNull.class)).as(describe(component)).isTrue();
        if (component.getType() == Long.class && field.isAnnotationPresent(Min.class))
          assertThat(field.getAnnotation(Min.class).value())
              .as(describe(component))
              .isGreaterThanOrEqualTo(0L);
      }
    }
  }

  private static Class<?> listElementType(RecordComponent component) {
    return (Class<?>) ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
  }

  private static boolean validatesElements(RecordComponent component) {
    Field field = backingField(component);
    return field.isAnnotationPresent(Valid.class)
        || (field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterized
            && parameterized.getAnnotatedActualTypeArguments()[0].isAnnotationPresent(Valid.class));
  }

  private static boolean forbidsNullElements(RecordComponent component) {
    return backingField(component).getAnnotatedType()
            instanceof AnnotatedParameterizedType parameterized
        && parameterized.getAnnotatedActualTypeArguments()[0].isAnnotationPresent(NotNull.class);
  }

  private static boolean restrictsToScopes(RecordComponent component) {
    if (!(backingField(component).getAnnotatedType()
        instanceof AnnotatedParameterizedType parameterized)) {
      return false;
    }
    jakarta.validation.constraints.Pattern pattern =
        parameterized.getAnnotatedActualTypeArguments()[0].getAnnotation(
            jakarta.validation.constraints.Pattern.class);
    if (pattern == null) {
      return false;
    }
    Pattern compiled = Pattern.compile(pattern.regexp());
    return Stream.of("ingest", "read", "admin").allMatch(scope -> compiled.matcher(scope).matches())
        && Stream.of("", "write", "readx", "admin,read")
            .noneMatch(scope -> compiled.matcher(scope).matches());
  }

  private static Class<?> requestRecord(String simpleName) {
    return requestRecords().stream()
        .filter(record -> record.getSimpleName().equals(simpleName))
        .findFirst()
        .orElseThrow(() -> new AssertionError(simpleName + "를 찾지 못했다"));
  }

  private static boolean isKnownType(Parameter parameter) {
    if (parameter.getType() == Map.class) {
      return parameter.isAnnotationPresent(RequestParam.class)
          && parameter.isAnnotationPresent(StorableJson.class)
          && parameter.getParameterizedType() instanceof ParameterizedType type
          && java.util.Arrays.equals(
              type.getActualTypeArguments(),
              new java.lang.reflect.Type[] {String.class, String.class});
    }
    if (parameter.getType() == HttpServletResponse.class
        || parameter.getType() == HttpServletRequest.class) {
      return true;
    }
    if (parameter.getType() == AuthenticatedPrincipal.class) {
      return parameter.isAnnotationPresent(AuthenticationPrincipal.class);
    }
    if (parameter.getType() == List.class) {
      return carriesText(parameter);
    }
    return KNOWN_PARAMETER_SCALAR_TYPES.contains(parameter.getType());
  }

  private static String describe(Method method, Parameter parameter) {
    return method.getDeclaringClass().getSimpleName()
        + "."
        + method.getName()
        + "("
        + parameter.getName()
        + ")";
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
