package com.meterengine.metric.repository;

import com.meterengine.global.math.DecimalFraction;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.MetricAggregation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BillableMetricUsageRepository {

  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  BillableMetricUsageRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  public record AggregatedQuantities(
      Map<UUID, DimensionUsage> totalByCustomerId,
      Map<UUID, List<DimensionUsage>> dimensionUsagesByCustomerId) {}

  // SUM, COUNT, MIN, MAX만으로 계산되는 집계. 이 미터들은 event를 한 번 읽어 함께 집계한다.
  private static final Set<String> ONE_PASS = Set.of("sum", "count", "min", "max", "avg");

  private record Scope(UUID organizationId, UUID environmentScopeId) {}

  // metrics와 같은 순서로 집계 결과를 돌려준다.
  public List<AggregatedQuantities> aggregateAll(
      List<BillableMetric> metrics,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore) {
    Map<BillableMetric, AggregatedQuantities> results = new HashMap<>();
    Map<Scope, List<BillableMetric>> onePass = new LinkedHashMap<>();
    for (BillableMetric metric : metrics) {
      if (ONE_PASS.contains(metric.getAggregation()))
        onePass
            .computeIfAbsent(
                new Scope(
                    metric.getOrganizationId(),
                    BusinessEnvironmentContext.scopeId(metric.getEnvironmentId())),
                ignored -> new ArrayList<>())
            .add(metric);
      else results.put(metric, aggregate(metric, start, end, receivedBefore));
    }
    onePass.forEach(
        (scope, scoped) ->
            results.putAll(aggregateOnePass(scope, scoped, start, end, receivedBefore)));
    return metrics.stream().map(results::get).toList();
  }

  private Map<BillableMetric, AggregatedQuantities> aggregateOnePass(
      Scope scope,
      List<BillableMetric> metrics,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore) {
    List<Map<String, Object>> definitions = new ArrayList<>();
    for (int index = 0; index < metrics.size(); index++) {
      BillableMetric metric = metrics.get(index);
      Map<String, Object> definition = new HashMap<>();
      definition.put("metric_index", index);
      definition.put("event_type", metric.getEventType());
      definition.put("target_property", metric.getTargetProperty());
      definition.put("dimension_properties", metric.getDimensionProperties());
      definitions.add(definition);
    }
    String query =
        """
        WITH metric AS (
          SELECT * FROM jsonb_to_recordset(?::jsonb)
            AS metric(metric_index int, event_type text, target_property text, dimension_properties jsonb)
        ), candidates AS (
          SELECT metric.metric_index, event.customer_id,
                 event.properties -> metric.target_property AS value,
                 COALESCE((SELECT jsonb_object_agg(dimension_property, event.properties -> dimension_property)
                           FROM jsonb_array_elements_text(metric.dimension_properties) AS dimension_property), '{}'::jsonb) AS dimension_values
          FROM event JOIN metric ON metric.event_type = event.type
          WHERE event.organization_id = ? AND event.environment_scope_id = ? AND event.type = ANY(?::text[])
            AND event.occurred_at >= ? AND event.occurred_at < ? AND event.received_at < ?
        ), eligible AS (
          SELECT *, CASE WHEN jsonb_typeof(value) = 'number' THEN value::text::numeric END AS numeric_value
          FROM candidates
        )
        SELECT metric_index, customer_id, dimension_values, GROUPING(dimension_values) = 1 AS is_total,
               COUNT(*)::numeric AS row_count, COUNT(numeric_value)::numeric AS numeric_count,
               SUM(numeric_value) AS sum_value, MIN(numeric_value) AS min_value, MAX(numeric_value) AS max_value
        FROM eligible
        GROUP BY GROUPING SETS ((metric_index, customer_id, dimension_values), (metric_index, customer_id))
        ORDER BY metric_index, customer_id, is_total, dimension_values
        """;
    List<Map<UUID, DimensionUsage>> totals = new ArrayList<>();
    List<Map<UUID, List<DimensionUsage>>> dimensions = new ArrayList<>();
    metrics.forEach(
        ignored -> {
          totals.add(new HashMap<>());
          dimensions.add(new HashMap<>());
        });
    jdbcTemplate.query(
        query,
        statement -> {
          statement.setString(1, jsonMapper.writeValueAsString(definitions));
          statement.setObject(2, scope.organizationId());
          statement.setObject(3, scope.environmentScopeId());
          statement.setArray(
              4,
              statement
                  .getConnection()
                  .createArrayOf(
                      "text",
                      metrics.stream().map(BillableMetric::getEventType).distinct().toArray()));
          statement.setObject(5, start);
          statement.setObject(6, end);
          statement.setObject(7, receivedBefore);
        },
        result -> {
          int index = result.getInt("metric_index");
          BillableMetric metric = metrics.get(index);
          BigDecimal numericCount = result.getBigDecimal("numeric_count");
          boolean count = "count".equals(metric.getAggregation());
          // 숫자 값이 없는 묶음은 미터별 집계에서도 행이 생기지 않는다.
          if (!count && numericCount.signum() == 0) return;
          BigDecimal sum = result.getBigDecimal("sum_value");
          BigDecimal raw =
              switch (metric.getAggregation()) {
                case "count" -> result.getBigDecimal("row_count");
                case "min" -> result.getBigDecimal("min_value");
                case "max" -> result.getBigDecimal("max_value");
                case "avg" -> new DecimalFraction(sum, numericCount).decimalValue(0);
                default -> sum;
              };
          boolean average = "avg".equals(metric.getAggregation());
          collect(
              totals.get(index),
              dimensions.get(index),
              metric,
              result.getObject("customer_id", UUID.class),
              result.getBoolean("is_total"),
              result.getString("dimension_values"),
              raw,
              average ? sum : raw,
              average ? numericCount : BigDecimal.ONE);
        });
    Map<BillableMetric, AggregatedQuantities> results = new HashMap<>();
    for (int index = 0; index < metrics.size(); index++)
      results.put(
          metrics.get(index), new AggregatedQuantities(totals.get(index), dimensions.get(index)));
    return results;
  }

  private void collect(
      Map<UUID, DimensionUsage> totals,
      Map<UUID, List<DimensionUsage>> dimensions,
      BillableMetric metric,
      UUID customer,
      boolean total,
      String dimensionValues,
      BigDecimal raw,
      BigDecimal numerator,
      BigDecimal denominator) {
    DimensionUsage usage =
        new DimensionUsage(
            total ? Map.of() : jsonMapper.readValue(dimensionValues, DIMENSION_VALUES_TYPE),
            MetricAggregation.transform(raw, numerator, denominator, metric),
            raw,
            numerator,
            denominator);
    if (total) totals.put(customer, usage);
    else dimensions.computeIfAbsent(customer, ignored -> new ArrayList<>()).add(usage);
  }

  // 그 달에 발생했고 receivedBefore 전에 받은 이벤트를 집계한다.
  public AggregatedQuantities aggregate(
      BillableMetric metric,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore) {
    if (!MetricAggregation.SUPPORTED.contains(metric.getAggregation()))
      throw new IllegalStateException("Unsupported metric aggregation");
    String filter =
        switch (metric.getAggregation()) {
          case "count" -> "TRUE";
          case "count_distinct" -> "jsonb_typeof(value) IN ('number', 'string', 'boolean')";
          default -> "jsonb_typeof(value) = 'number'";
        };
    boolean average = "avg".equals(metric.getAggregation());
    boolean percentile = List.of("median", "percentile").contains(metric.getAggregation());
    String expression =
        switch (metric.getAggregation()) {
          case "sum" -> "SUM(numeric_value)";
          case "count" -> "COUNT(*)::numeric";
          case "count_distinct" -> "COUNT(DISTINCT value)::numeric";
          case "min" -> "MIN(numeric_value)";
          case "max" -> "MAX(numeric_value)";
          case "avg" -> "AVG(numeric_value)";
          case "first" ->
              "(array_agg(numeric_value ORDER BY occurred_at, transaction_id COLLATE \"C\"))[1]";
          case "last" ->
              "(array_agg(numeric_value ORDER BY occurred_at DESC, transaction_id COLLATE \"C\""
                  + " DESC))[1]";
          case "median", "percentile" -> "array_agg(numeric_value ORDER BY numeric_value)";
          default -> throw new IllegalStateException("Unsupported metric aggregation");
        };
    String query =
        """
        WITH candidates AS (
          SELECT customer_id, occurred_at, transaction_id,
                 properties -> ?::text AS value,
                 COALESCE((SELECT jsonb_object_agg(dimension_property, properties -> dimension_property)
                           FROM unnest(?::text[]) AS dimension_property), '{}'::jsonb) AS dimension_values
          FROM event WHERE organization_id = ? AND environment_scope_id = ? AND type = ? AND occurred_at >= ? AND occurred_at < ? AND received_at < ?
        ), eligible AS (
          SELECT *, CASE WHEN jsonb_typeof(value) = 'number' THEN value::text::numeric END AS numeric_value
          FROM candidates WHERE %s
        ), groups AS (
          SELECT customer_id, dimension_values, GROUPING(dimension_values) = 1 AS is_total,
                 %s AS %s%s
          FROM eligible GROUP BY GROUPING SETS ((customer_id, dimension_values), (customer_id))
        )
        %s
        ORDER BY customer_id, is_total, dimension_values
        """
            .formatted(
                filter,
                expression,
                percentile ? "ordered_values" : "quantity",
                average
                    ? ", SUM(numeric_value) AS numerator, COUNT(*)::numeric AS denominator"
                    : "",
                percentile
                    ? """
                    SELECT computed.*, quantity AS numerator, 1::numeric AS denominator FROM (
                    SELECT customer_id, dimension_values, is_total,
                      ordered_values[floor(position)::int + 1]
                        + (ordered_values[ceil(position)::int + 1] - ordered_values[floor(position)::int + 1])
                          * (position - floor(position)) AS quantity
                    FROM groups CROSS JOIN LATERAL (
                      SELECT (cardinality(ordered_values) - 1) * ?::numeric * 0.01 AS position
                    ) percentile_position
                    ) computed
                    """
                    : average
                        ? "SELECT customer_id, dimension_values, is_total, quantity, numerator,"
                            + " denominator FROM groups"
                        : "SELECT customer_id, dimension_values, is_total, quantity, quantity AS"
                            + " numerator, 1::numeric AS denominator FROM groups");
    Map<UUID, DimensionUsage> totals = new HashMap<>();
    Map<UUID, List<DimensionUsage>> dimensions = new HashMap<>();
    jdbcTemplate.query(
        query,
        statement -> {
          statement.setString(1, metric.getTargetProperty());
          statement.setArray(
              2,
              statement
                  .getConnection()
                  .createArrayOf("text", metric.getDimensionProperties().toArray(String[]::new)));
          statement.setObject(3, metric.getOrganizationId());
          statement.setObject(4, BusinessEnvironmentContext.scopeId(metric.getEnvironmentId()));
          statement.setString(5, metric.getEventType());
          statement.setObject(6, start);
          statement.setObject(7, end);
          statement.setObject(8, receivedBefore);
          if (percentile)
            statement.setBigDecimal(
                9,
                "median".equals(metric.getAggregation())
                    ? new BigDecimal("50")
                    : metric.getPercentile());
        },
        result -> {
          BigDecimal raw = result.getBigDecimal("quantity");
          BigDecimal numerator = result.getBigDecimal("numerator");
          BigDecimal denominator = result.getBigDecimal("denominator");
          if (average) raw = new DecimalFraction(numerator, denominator).decimalValue(0);
          collect(
              totals,
              dimensions,
              metric,
              result.getObject("customer_id", UUID.class),
              result.getBoolean("is_total"),
              result.getString("dimension_values"),
              raw,
              numerator,
              denominator);
        });
    return new AggregatedQuantities(totals, dimensions);
  }
}
