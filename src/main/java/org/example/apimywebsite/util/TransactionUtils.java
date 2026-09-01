package org.example.apimywebsite.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// COR-001/COR-002 fix: shared helper for deferring irreversible or externally-visible side
// effects (remote deletes, WebSocket broadcasts) until the current database transaction has
// actually committed, instead of running them eagerly mid-transaction where a later rollback
// would leave them pointing at data that was never durably persisted. Falls back to running
// immediately when no transaction is active, so behavior is unchanged for any caller invoked
// outside a transactional context (including plain unit tests with no real transaction manager).
public final class TransactionUtils {

    private TransactionUtils() {
    }

    public static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
