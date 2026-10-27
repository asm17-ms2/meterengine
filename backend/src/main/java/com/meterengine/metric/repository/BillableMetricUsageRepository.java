package com.meterengine.metric.repository;

import com.meterengine.global.math.DecimalFraction;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.MetricAggregation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  public Map<UUID, List<DimensionUsage>> sumQuantityByCustomerIdAndDimensionValues(
      UUID organizationId,
      String eventType,
      String targetProperty,
      List<String> dimensionProperties,
      OffsetDateTime start,
      OffsetDateTime end) {
    return sumQuantityByCustomerIdAndDimensionValues(
        organizationId, null, eventType, targetProperty, dimensionProperties, start, end);
  }

  public Map<UUID, List<DimensionUsage>> sumQuantityByCustomerIdAndDimensionValues(
      UUID organizationId,
      UUID environmentId,
      String eventType,
      String targetProperty,
      List<String> dimensionProperties,
      OffsetDateTime start,
      OffsetDateTime end) {
    return aggregate(
            new BillableMetric(
                organizationId,
                "",
                "",
                eventType,
                "sum",
                targetProperty,
                dimensionProperties,
                BigDecimal.ONE,
                "none",
                0,
                null,
                environmentId),
            start,
            end)
        .dimensionUsagesByCustomerId();
  }

  public AggregatedQuantities aggregate(
      BillableMetric metric, OffsetDateTime start, OffsetDateTime end) {
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
          FROM event WHERE organization_id = ? AND environment_id IS NOT DISTINCT FROM ?::uuid AND type = ? AND occurred_at >= ? AND occurred_at < ?
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
          statement.setObject(4, metric.getEnvironmentId());
          statement.setString(5, metric.getEventType());
          statement.setObject(6, start);
          statement.setObject(7, end);
          if (percentile)
            statement.setBigDecimal(
                8,
                "median".equals(metric.getAggregation())
                    ? new BigDecimal("50")
                    : metric.getPercentile());
        },
        result -> {
          UUID customer = result.getObject("customer_id", UUID.class);
          boolean total = result.getBoolean("is_total");
          BigDecimal raw = result.getBigDecimal("quantity");
          BigDecimal numerator = result.getBigDecimal("numerator");
          BigDecimal denominator = result.getBigDecimal("denominator");
          if (average) raw = new DecimalFraction(numerator, denominator).decimalValue(0);
          DimensionUsage usage =
              new DimensionUsage(
                  total
                      ? Map.of()
                      : jsonMapper.readValue(
                          result.getString("dimension_values"), DIMENSION_VALUES_TYPE),
                  MetricAggregation.transform(raw, numerator, denominator, metric),
                  raw,
                  numerator,
                  denominator);
          if (total) totals.put(customer, usage);
          else dimensions.computeIfAbsent(customer, ignored -> new ArrayList<>()).add(usage);
        });
    return new AggregatedQuantities(totals, dimensions);
  }
}
