package com.meterengine.metric.repository;

import com.meterengine.metric.dto.DimensionUsage;
import java.sql.ResultSet;
import java.sql.SQLException;
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

  private static final String SUM_QUANTITY_BY_CUSTOMER_ID_AND_DIMENSION_VALUES =
      """
      SELECT customer_id,
             COALESCE((SELECT jsonb_object_agg(dimension_property, properties -> dimension_property)
                       FROM unnest(?::text[]) AS dimension_property),
                      '{}'::jsonb) AS dimension_values,
             SUM((properties ->> ?::text)::numeric) AS quantity
      FROM event
      WHERE organization_id = ?
        AND type = ?
        AND occurred_at >= ?
        AND occurred_at < ?
        AND received_at < ?
        AND jsonb_typeof(properties -> ?::text) = 'number'
      GROUP BY 1, 2
      ORDER BY 1, 2
      """;

  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  BillableMetricUsageRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  public Map<UUID, List<DimensionUsage>> sumQuantityByCustomerIdAndDimensionValues(
      UUID organizationId,
      String eventType,
      String targetProperty,
      List<String> dimensionProperties,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore) {
    Map<UUID, List<DimensionUsage>> dimensionUsagesByCustomerId = new HashMap<>();
    jdbcTemplate.query(
        SUM_QUANTITY_BY_CUSTOMER_ID_AND_DIMENSION_VALUES,
        preparedStatement -> {
          preparedStatement.setArray(
              1,
              preparedStatement
                  .getConnection()
                  .createArrayOf("text", dimensionProperties.toArray(String[]::new)));
          preparedStatement.setString(2, targetProperty);
          preparedStatement.setObject(3, organizationId);
          preparedStatement.setString(4, eventType);
          preparedStatement.setObject(5, start);
          preparedStatement.setObject(6, end);
          preparedStatement.setObject(7, receivedBefore);
          preparedStatement.setString(8, targetProperty);
        },
        resultSet -> {
          dimensionUsagesByCustomerId
              .computeIfAbsent(
                  resultSet.getObject("customer_id", UUID.class), customerId -> new ArrayList<>())
              .add(toDimensionUsage(resultSet));
        });
    return dimensionUsagesByCustomerId;
  }

  private DimensionUsage toDimensionUsage(ResultSet resultSet) throws SQLException {
    return new DimensionUsage(
        jsonMapper.readValue(resultSet.getString("dimension_values"), DIMENSION_VALUES_TYPE),
        resultSet.getBigDecimal("quantity"));
  }
}
