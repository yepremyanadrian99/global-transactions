package am.adrian.global.transactions.service;

import am.adrian.global.transactions.domain.TransactionalContext;
import am.adrian.global.transactions.domain.TransactionalOperation;
import am.adrian.global.transactions.exception.GlobalTransactionException;
import am.adrian.global.transactions.service.helper.RequestContextHolder;

/**
 * Class responsible for executing all instances of the {@link TransactionalOperation}.
 * This class should always be used for executing the operations,
 * otherwise they won't be cached and reverted later if exception happens at some step.
 * This class is designed for usage in non-reactive applications.
 */
public class TransactionalOperationExecutor {

    public <T, R> R executeInsideTransaction(TransactionalOperation<T, R> operation, T data) {
        var context = RequestContextHolder.getTransactionalContext();
        try {
            var result = operation.apply(data);
            context.addOperation(operation);
            return result;
        } catch (Throwable e) {
            return handleError(context, e);
        }
    }

    private <R> R handleError(TransactionalContext context, Throwable e) {
        TransactionalOperation<?, ?> nextOperation;
        while ((nextOperation = context.popOperation()) != null) {
            nextOperation.revert();
        }

        throw new GlobalTransactionException(e);
    }
}
