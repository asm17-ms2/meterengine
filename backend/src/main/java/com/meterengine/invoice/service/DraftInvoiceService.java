package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.CustomerEntry;
import com.meterengine.invoice.dto.DraftInvoiceResponse.LineEntry;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.MetricUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.UsageAggregationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객별 청구 예정액 계산 (MS2-124).
 *
 * <p>집계 서비스가 낸 미터별 사용량에 단가를 곱해 금액을 만든다. 스냅샷 테이블을 두지 않고 조회할 때마다 그 시점의 이벤트로 다시 계산한다 (인수 조건 "집계시점은 조회시
 * 계산").
 *
 * <p>금액은 BigDecimal로만 계산하고 마지막에 원 단위 정수로 만든다. 단가가 NUMERIC이라 토큰당 1원 미만이 가능한데, double을 거치면 청구 근거가 조용히
 * 어긋난다 (V1 마이그레이션의 단가 주석 참조: 금액 계산의 정수 연산 보장은 계산 로직 소관).
 */
@Service
public class DraftInvoiceService {

  private final UsageAggregationService aggregation;
  private final CustomerRepository customers;

  DraftInvoiceService(UsageAggregationService aggregation, CustomerRepository customers) {
    this.aggregation = aggregation;
    this.customers = customers;
  }

  /**
   * 지정 월의 고객별 청구 예정액을 낸다.
   *
   * <p>고객 목록을 여기서 다시 조회해 기준으로 삼는다. 미터가 하나도 없는 도입사는 집계가 고객을 조회하지 않고 빈 결과를 내는데, 그 경우에도 모든 고객이 금액 0으로
   * 응답에 나와야 한다 (인수 조건). 모르는 도입사는 고객도 미터도 없어 자연히 빈 결과가 된다 (사용량 API와 같은 동작, MS2-126이 붙으면 401로 막힌다).
   *
   * <p>읽기 트랜잭션으로 묶어 고객 조회와 집계가 같은 커넥션에서 실행되게 한다. 기본 격리(READ COMMITTED)에서는 문장마다 스냅샷이 새로 잡혀 두 조회의 고객
   * 집합이 어긋날 수 있으므로, 피벗은 목록 일치를 전제하지 않고 customerId로 찾되 없으면 0으로 채운다.
   */
  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(UsageAggregationService.BILLING_ZONE);

    List<Customer> organizationCustomers =
        customers.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
    List<MetricQuantities> metricQuantities =
        aggregation.aggregate(organizationId, month).stream().map(MetricQuantities::from).toList();

    List<CustomerEntry> entries =
        organizationCustomers.stream()
            .map(customer -> customerEntry(customer, metricQuantities))
            .toList();
    long totalAmount = entries.stream().mapToLong(CustomerEntry::amount).reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(month.toString(), calculatedAt, totalAmount, entries);
  }

  /** 고객 한 명의 소계와 라인들. 라인 순서는 집계 결과의 미터 순서(code 오름차순) 그대로다. */
  private static CustomerEntry customerEntry(
      Customer customer, List<MetricQuantities> metricQuantities) {
    List<LineEntry> lines =
        metricQuantities.stream().map(metric -> metric.lineFor(customer.getId())).toList();
    // sum()은 오버플로에서 조용히 랩어라운드한다. charge()가 예외를 택한 이유가 합산에서 무너지지 않게 addExact로 합친다.
    long amount = lines.stream().mapToLong(LineEntry::amount).reduce(0L, Math::addExact);
    return new CustomerEntry(customer.getId(), customer.getName(), amount, lines);
  }

  /**
   * 미터 하나의 고객별 수량을 customerId로 찾는 형태로 뒤집은 것.
   *
   * <p>집계 결과와 고객 목록을 인덱스로 짝짓지 않기 위해서다. 지금은 두 목록의 순서가 같지만, 그건 집계 구현의 사정이지 이 코드가 기댈 계약이 아니다.
   */
  private record MetricQuantities(BillableMetric metric, Map<UUID, BigDecimal> byCustomer) {

    static MetricQuantities from(MetricUsage usage) {
      return new MetricQuantities(
          usage.metric(),
          usage.customers().stream()
              .collect(Collectors.toMap(CustomerUsage::customerId, CustomerUsage::quantity)));
    }

    LineEntry lineFor(UUID customerId) {
      BigDecimal quantity = byCustomer.getOrDefault(customerId, BigDecimal.ZERO);
      return new LineEntry(
          metric.getCode(),
          metric.getTargetProperty(),
          quantity,
          metric.getUnitPrice(),
          charge(quantity, metric.getUnitPrice()));
    }
  }

  /**
   * 수량 x 단가를 원 단위 정수로 만든다.
   *
   * <p>원 미만은 라인마다 절사한다 (팀 결정 2026-08-13). 합산 후에 한 번만 절사하면 라인 금액의 합과 고객 소계가 어긋나, 화면에 보이는 줄을 다 더해도
   * 소계가 안 맞는 표가 된다.
   *
   * <p>{@code longValueExact()}라서 long 범위를 넘으면 조용히 잘리는 대신 예외로 터진다. 청구 금액이 잘못 나가는 것보다 500이 낫다.
   */
  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(0, RoundingMode.DOWN).longValueExact();
  }
}
