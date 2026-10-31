package com.meterengine.global.database;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class PublicResourceLocator {
  private final JdbcTemplate jdbc;
  private final ScopeTransactions transactions;

  public PublicResourceLocator(JdbcTemplate jdbc, ScopeTransactions transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  public Optional<Route> invoice(byte[] hash) {
    return locate(
        "SELECT scope,organization_id,link_id AS id FROM shared.invoice_view_link_route WHERE token_hash=?",
        hash);
  }

  public Optional<Route> registration(String hash) {
    return locate(
        "SELECT scope,organization_id,registration_id AS id FROM shared.payment_registration_route WHERE token_hash=?",
        hash);
  }

  public Optional<Route> order(UUID organization, UUID order) {
    return locate(
        "SELECT scope,organization_id,order_id AS id FROM shared.payment_order_route WHERE organization_id=? AND order_id=?",
        organization,
        order);
  }

  public boolean orderInOtherScope(UUID organization, UUID order, DatabaseScope selected) {
    return transactions.read(
        DatabaseScope.SHARED,
        () ->
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM shared.payment_order_route WHERE organization_id=? AND order_id=? AND scope<>?)",
                    Boolean.class,
                    organization,
                    order,
                    selected.name().toLowerCase(Locale.ROOT))));
  }

  private Optional<Route> locate(String sql, Object... arguments) {
    List<Route> matches =
        transactions.read(
            DatabaseScope.SHARED,
            () ->
                jdbc.query(
                    sql,
                    (rs, index) ->
                        new Route(
                            DatabaseScope.valueOf(rs.getString("scope").toUpperCase(Locale.ROOT)),
                            rs.getObject("organization_id", UUID.class),
                            rs.getObject("id", UUID.class)),
                    arguments));
    return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
  }

  public record Route(DatabaseScope scope, UUID organizationId, UUID id) {}
}
