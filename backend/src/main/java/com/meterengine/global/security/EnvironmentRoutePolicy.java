package com.meterengine.global.security;

import com.meterengine.global.database.DatabaseScope;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

public final class EnvironmentRoutePolicy {
  private static final Contract CONTRACT = load();

  private EnvironmentRoutePolicy() {}

  public static Route classify(String path) {
    String candidate = path.split("[?#]", 2)[0];
    for (var route : CONTRACT.routes()) if (matches(route.path(), candidate)) return route;
    return CONTRACT.fallback();
  }

  private static boolean matches(String template, String path) {
    String[] expected = template.split("/", -1);
    String[] actual = path.split("/", -1);
    if (expected.length != actual.length) return false;
    for (int index = 0; index < expected.length; index++) {
      if (expected[index].matches("\\{[a-z][a-z0-9_]*\\}")) {
        if (actual[index].isEmpty()) return false;
      } else if (!expected[index].equals(actual[index])) return false;
    }
    return true;
  }

  private static Contract load() {
    try (var stream =
        EnvironmentRoutePolicy.class.getResourceAsStream("/contracts/environment-routes.json")) {
      var root = JsonMapper.builder().build().readTree(stream);
      if (root.path("schema_version").asInt() != 1)
        throw new IllegalStateException("Unsupported environment route contract");
      var routes = new ArrayList<Route>();
      for (var row : root.path("routes")) {
        String entryScope = row.path("entry_scope").asString();
        if (!entryScope.equals("shared"))
          throw new IllegalStateException("Unsupported explicit route scope");
        routes.add(
            new Route(
                row.path("path").asString(),
                row.path("environment_required").asBoolean(),
                "shared"));
      }
      var fallback = root.path("default");
      if (!fallback.path("entry_scope").asString().equals("business")
          || !fallback.path("environment_required").asBoolean())
        throw new IllegalStateException("Unsupported default environment route");
      return new Contract(new Route(null, true, "business"), List.copyOf(routes));
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private record Contract(Route fallback, List<Route> routes) {}

  public record Route(String path, boolean environmentRequired, String entryScope) {
    public DatabaseScope databaseScope(BusinessEnvironment environment) {
      return entryScope.equals("shared") ? DatabaseScope.SHARED : environment.databaseScope();
    }
  }
}
