package com.meterengine.query.repository;

import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import com.meterengine.query.dto.PriceSearchResponse;
import com.meterengine.query.dto.SearchPageResponse;
import com.meterengine.query.service.SearchParameters;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class WorkspaceQueryRepository {
  private static final TypeReference<Map<String, Object>> DIMENSIONS = new TypeReference<>() {};
  private static final String PRICE_SCOPE =
      """
      WITH scope AS (
        SELECT cast(? AS uuid) AS organization_id, cast(? AS uuid) AS customer_id, cast(? AS varchar) AS period, cast(? AS uuid) AS environment_id
      ), eligible AS (
        SELECT pr.*, row_number() OVER (
          PARTITION BY pr.billable_metric_code, pr.dimension_values ORDER BY pr.effective_from DESC, pr.id
        ) AS position
        FROM price_rate pr CROSS JOIN scope p
        WHERE pr.organization_id = p.organization_id
          AND pr.environment_id IS NOT DISTINCT FROM p.environment_id
          AND pr.customer_id IS NOT DISTINCT FROM p.customer_id
          AND ((pr.effective_from <= p.period
            AND (pr.effective_until IS NULL OR pr.effective_until > p.period)
            AND (pr.archived_from IS NULL OR pr.archived_from > p.period))
            OR (pr.archived_from IS NOT NULL AND pr.archived_from <= p.period
              AND pr.effective_from < pr.archived_from
              AND (pr.effective_until IS NULL OR pr.effective_until > p.period)))
      ), rates AS (
        SELECT e.* FROM eligible e CROSS JOIN scope p
        WHERE e.position = 1 AND e.effective_from <= p.period
          AND (e.effective_until IS NULL OR e.effective_until > p.period)
          AND (e.archived_from IS NULL OR e.archived_from > p.period)
      ), metric_counts AS (
        SELECT bm.*, count(r.id) AS rate_count
        FROM billable_metric bm CROSS JOIN scope p
        LEFT JOIN rates r ON r.organization_id = bm.organization_id AND r.environment_scope_id = bm.environment_scope_id AND r.billable_metric_code = bm.code
        WHERE bm.organization_id = p.organization_id
          AND bm.environment_id IS NOT DISTINCT FROM p.environment_id
        GROUP BY bm.organization_id, bm.environment_scope_id, bm.code
      )
      """;
  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  WorkspaceQueryRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  public SearchPageResponse<CustomerResponse> customers(
      UUID organizationId, SearchParameters search) {
    String sort =
        search.sortOr(
            "name",
            Set.of("name", "external_id", "created_at", "archived_at", "id", "billing_email"));
    String base =
        " FROM customer WHERE organization_id = ? AND environment_id IS NOT DISTINCT FROM cast(? as"
            + " uuid)";
    List<Object> baseValues = new ArrayList<>();
    baseValues.add(organizationId);
    baseValues.add(BusinessEnvironmentContext.environmentId());
    StringBuilder where = new StringBuilder(base);
    List<Object> values = new ArrayList<>(baseValues);
    archive(where, values, search.status());
    if (search.externalId() != null) {
      where.append(" AND external_id = ?");
      values.add(search.externalId());
    }
    if (search.customerId() != null) {
      where.append(" AND id = ?");
      values.add(search.customerId());
    }
    if (search.query() != null) {
      where.append(
          " AND (lower(name) LIKE ? ESCAPE '!' OR lower(coalesce(external_id, '')) LIKE ? ESCAPE"
              + " '!' OR lower(coalesce(billing_email, '')) LIKE ? ESCAPE '!')");
      String pattern = pattern(search.query());
      values.add(pattern);
      values.add(pattern);
      values.add(pattern);
    }
    long total = count(where.toString(), values);
    long unfiltered = count(base, baseValues);
    List<Object> paged = new ArrayList<>(values);
    paged.add(search.size());
    paged.add(search.offset());
    String ordering =
        sort.equals("name") || sort.equals("external_id") || sort.equals("billing_email")
            ? sort + " COLLATE korean"
            : sort;
    List<CustomerResponse> customers =
        jdbcTemplate.query(
            "SELECT id, name, billing_email, created_at, external_id, archived_at, version"
                + where
                + " ORDER BY "
                + ordering
                + (search.descending() ? " DESC" : " ASC")
                + " NULLS LAST, id ASC LIMIT ? OFFSET ?",
            (rs, row) ->
                new CustomerResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("billing_email"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getString("external_id"),
                    rs.getObject("archived_at", OffsetDateTime.class),
                    rs.getLong("version")),
            paged.toArray());
    long active = count(base + " AND archived_at IS NULL", baseValues);
    return new SearchPageResponse<>(
        customers,
        total,
        unfiltered,
        search.page(),
        search.size(),
        Map.of("active_count", active, "archived_count", unfiltered - active));
  }

  public SearchPageResponse<BillableMetricResponse> metrics(
      UUID organizationId, SearchParameters search) {
    String sort =
        search.sortOr("code", Set.of("code", "name", "event_type", "aggregation", "archived_at"));
    String base =
        " FROM billable_metric WHERE organization_id = ? AND environment_id IS NOT DISTINCT FROM"
            + " cast(? as uuid)";
    List<Object> baseValues =
        Arrays.asList(organizationId, BusinessEnvironmentContext.environmentId());
    StringBuilder where = new StringBuilder(base);
    List<Object> values = new ArrayList<>(baseValues);
    archive(where, values, search.status());
    if (search.metricCode() != null) {
      where.append(" AND code = ?");
      values.add(search.metricCode());
    }
    if (search.query() != null) {
      where.append(
          " AND (lower(code) LIKE ? ESCAPE '!' OR lower(name) LIKE ? ESCAPE '!' OR"
              + " lower(event_type) LIKE ? ESCAPE '!')");
      String pattern = pattern(search.query());
      values.add(pattern);
      values.add(pattern);
      values.add(pattern);
    }
    long total = count(where.toString(), values);
    long unfiltered = count(base, baseValues);
    List<Object> paged = new ArrayList<>(values);
    paged.add(search.size());
    paged.add(search.offset());
    String ordering = sort.equals("archived_at") ? sort : sort + " COLLATE korean";
    List<BillableMetricResponse> metrics =
        jdbcTemplate.query(
            "SELECT code, name, event_type, aggregation, target_property, dimension_properties,"
                + " archived_at, active_from, inactive_from, unit_size, rounding_mode,"
                + " rounding_precision, percentile, version"
                + where
                + " ORDER BY "
                + ordering
                + (search.descending() ? " DESC" : " ASC")
                + " NULLS LAST, code COLLATE korean ASC LIMIT ? OFFSET ?",
            WorkspaceQueryRepository::metric,
            paged.toArray());
    long active = count(base + " AND archived_at IS NULL", baseValues);
    return new SearchPageResponse<>(
        metrics,
        total,
        unfiltered,
        search.page(),
        search.size(),
        Map.of("active_count", active, "archived_count", unfiltered - active));
  }

  public SearchPageResponse<PriceSearchResponse> prices(
      UUID organizationId, SearchParameters search) {
    String sort =
        search.sortOr(
            "code",
            Set.of(
                "code",
                "name",
                "billable_metric_code",
                "billable_metric_name",
                "rate_count",
                "archived_at"));
    sort =
        switch (sort) {
          case "billable_metric_code" -> "code";
          case "billable_metric_name" -> "name";
          default -> sort;
        };
    List<Object> scope = new ArrayList<>();
    scope.add(organizationId);
    scope.add(search.customerId());
    scope.add(search.monthOrCurrent().toString());
    scope.add(BusinessEnvironmentContext.environmentId());
    StringBuilder where = new StringBuilder(" FROM metric_counts WHERE true");
    List<Object> values = new ArrayList<>(scope);
    archive(where, values, search.status());
    if (search.metricCode() != null) {
      where.append(" AND code = ?");
      values.add(search.metricCode());
    }
    if (search.query() != null) {
      where.append(" AND (lower(code) LIKE ? ESCAPE '!' OR lower(name) LIKE ? ESCAPE '!')");
      values.add(pattern(search.query()));
      values.add(pattern(search.query()));
    }
    long total =
        jdbcTemplate.queryForObject(
            PRICE_SCOPE + "SELECT count(*)" + where, Long.class, values.toArray());
    long unfiltered =
        jdbcTemplate.queryForObject(
            PRICE_SCOPE + "SELECT count(*) FROM metric_counts", Long.class, scope.toArray());
    long missing =
        jdbcTemplate.queryForObject(
            PRICE_SCOPE + "SELECT count(*)" + where + " AND rate_count = 0",
            Long.class,
            values.toArray());
    List<Object> paged = new ArrayList<>(values);
    paged.add(search.size());
    paged.add(search.offset());
    String ordering = sort.equals("code") || sort.equals("name") ? sort + " COLLATE korean" : sort;
    List<BillableMetricResponse> metrics =
        jdbcTemplate.query(
            PRICE_SCOPE
                + "SELECT *"
                + where
                + " ORDER BY "
                + ordering
                + (search.descending() ? " DESC" : " ASC")
                + " NULLS LAST, code COLLATE korean ASC LIMIT ? OFFSET ?",
            WorkspaceQueryRepository::metric,
            paged.toArray());
    Map<String, List<BillableMetricPricePriceRate>> rates = new HashMap<>();
    if (!metrics.isEmpty()) {
      List<Object> parameters = new ArrayList<>(scope);
      metrics.forEach(metric -> parameters.add(metric.code()));
      String slots = String.join(",", java.util.Collections.nCopies(metrics.size(), "?"));
      jdbcTemplate.query(
          PRICE_SCOPE
              + "SELECT * FROM rates WHERE billable_metric_code IN ("
              + slots
              + ") ORDER BY dimension_values",
          rs -> {
            String code = rs.getString("billable_metric_code");
            rates
                .computeIfAbsent(code, ignored -> new ArrayList<>())
                .add(
                    new BillableMetricPricePriceRate(
                        jsonMapper.readValue(rs.getString("dimension_values"), DIMENSIONS),
                        rs.getBigDecimal("unit_price"),
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("effective_from"),
                        rs.getString("effective_until"),
                        rs.getString("model"),
                        rs.getString("tiers"),
                        rs.getObject("archived_at", OffsetDateTime.class),
                        rs.getString("archived_from")));
          },
          parameters.toArray());
    }
    List<PriceSearchResponse> result =
        metrics.stream()
            .map(
                metric -> {
                  List<BillableMetricPricePriceRate> selected =
                      rates.getOrDefault(metric.code(), List.of());
                  return new PriceSearchResponse(
                      metric.code(),
                      metric.name(),
                      metric.dimensionProperties(),
                      selected,
                      metric.archivedAt(),
                      selected.size());
                })
            .toList();
    return new SearchPageResponse<>(
        result,
        total,
        unfiltered,
        search.page(),
        search.size(),
        Map.of(
            "month",
            search.monthOrCurrent().toString(),
            "metric_count",
            total,
            "no_rate_count",
            missing));
  }

  public List<String> months(UUID organizationId, String resource) {
    String sql =
        switch (resource) {
          case "invoices" ->
              "SELECT DISTINCT period AS month FROM invoice WHERE organization_id = ? AND"
                  + " environment_id IS NOT DISTINCT FROM cast(? as uuid) ORDER BY month DESC";
          case "billable-metric-prices" ->
              "SELECT DISTINCT effective_from AS month FROM price_rate WHERE organization_id = ?"
                  + " AND environment_id IS NOT DISTINCT FROM cast(? as uuid) ORDER BY month DESC";
          default ->
              "SELECT DISTINCT to_char(occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM') AS month"
                  + " FROM event WHERE organization_id = ? AND environment_id IS NOT DISTINCT FROM"
                  + " cast(? as uuid) ORDER BY month DESC";
        };
    return jdbcTemplate.query(
        sql,
        (rs, row) -> rs.getString("month"),
        organizationId,
        BusinessEnvironmentContext.environmentId());
  }

  private static BillableMetricResponse metric(ResultSet rs, int row) throws SQLException {
    return new BillableMetricResponse(
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("event_type"),
        rs.getString("aggregation"),
        rs.getString("target_property"),
        Arrays.asList((String[]) rs.getArray("dimension_properties").getArray()),
        rs.getObject("archived_at", OffsetDateTime.class),
        rs.getString("active_from"),
        rs.getString("inactive_from"),
        rs.getBigDecimal("unit_size"),
        rs.getString("rounding_mode"),
        rs.getInt("rounding_precision"),
        rs.getBigDecimal("percentile"),
        rs.getLong("version"));
  }

  private long count(String where, List<Object> values) {
    return jdbcTemplate.queryForObject("SELECT count(*)" + where, Long.class, values.toArray());
  }

  private static void archive(StringBuilder sql, List<Object> values, String status) {
    switch (ArchiveStatus.parse(status)) {
      case ACTIVE -> sql.append(" AND archived_at IS NULL");
      case ARCHIVED -> sql.append(" AND archived_at IS NOT NULL");
      case ALL -> {}
    }
  }

  private static String pattern(String query) {
    return "%"
        + query
            .toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        + "%";
  }
}
