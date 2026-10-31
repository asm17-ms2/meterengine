package com.meterengine.global.database;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

final class CutoverTransactions extends JpaTransactionManager {
  private final CutoverPolicy cutover;

  CutoverTransactions(EntityManagerFactory factory, CutoverPolicy cutover) {
    super(factory);
    this.cutover = cutover;
    setValidateExistingTransaction(cutover.readOnly());
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    if (!definition.isReadOnly()) cutover.requireWritable();
    super.doBegin(transaction, definition);
  }
}
