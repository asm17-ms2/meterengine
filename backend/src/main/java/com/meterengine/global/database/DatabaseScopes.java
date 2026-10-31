package com.meterengine.global.database;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DatabaseScopes {

  private static final ThreadLocal<DatabaseScope> CURRENT = new ThreadLocal<>();

  private DatabaseScopes() {}

  public static DatabaseScope current() {
    var scope = CURRENT.get();
    return scope == null ? DatabaseScope.SHARED : scope;
  }

  public static <T> T call(DatabaseScope scope, Supplier<T> action) {
    Objects.requireNonNull(scope, "database scope required");
    Objects.requireNonNull(action, "database action required");
    requireChangeAllowed(scope);
    var previous = CURRENT.get();
    CURRENT.set(scope);
    try {
      return action.get();
    } finally {
      requireChangeAllowed(previous == null ? DatabaseScope.SHARED : previous);
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }

  public static void clear() {
    requireChangeAllowed(DatabaseScope.SHARED);
    CURRENT.remove();
  }

  private static void requireChangeAllowed(DatabaseScope scope) {
    if (current() == scope) return;
    boolean boundEntityManager =
        TransactionSynchronizationManager.getResourceMap().values().stream()
            .anyMatch(
                resource ->
                    resource instanceof EntityManagerHolder holder
                        && holder.getEntityManager().isOpen());
    if (TransactionSynchronizationManager.isActualTransactionActive() || boundEntityManager) {
      throw new IllegalStateException(
          "database scope cannot change while an EntityManager or transaction is active");
    }
  }
}
