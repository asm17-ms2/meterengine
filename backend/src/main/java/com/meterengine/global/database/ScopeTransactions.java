package com.meterengine.global.database;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class ScopeTransactions {

  private final TransactionTemplate read;
  private final TransactionTemplate write;

  public ScopeTransactions(PlatformTransactionManager transactionManager) {
    read = template(transactionManager, true);
    write = template(transactionManager, false);
  }

  public <T> T read(DatabaseScope scope, Supplier<T> action) {
    return DatabaseScopes.call(scope, () -> read.execute(_ -> action.get()));
  }

  public <T> T write(DatabaseScope scope, Supplier<T> action) {
    return DatabaseScopes.call(scope, () -> write.execute(_ -> action.get()));
  }

  private static TransactionTemplate template(
      PlatformTransactionManager manager, boolean readOnly) {
    var result = new TransactionTemplate(manager);
    result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    result.setReadOnly(readOnly);
    return result;
  }
}
