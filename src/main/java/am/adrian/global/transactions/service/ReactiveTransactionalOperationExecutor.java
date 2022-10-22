package am.adrian.global.transactions.service;

import am.adrian.global.transactions.domain.TransactionalContext;
import am.adrian.global.transactions.domain.TransactionalOperation;
import am.adrian.global.transactions.exception.GlobalTransactionException;
import am.adrian.global.transactions.service.helper.ReactiveRequestContextHolder;
import reactor.core.publisher.Mono;

/**
 * Class responsible for executing all instances of the {@link TransactionalOperation}.
 * This class should always be used for executing the operations,
 * otherwise they won't be cached and reverted later if exception happens at some step.
 */
public class ReactiveTransactionalOperationExecutor {

    public <T, R> Mono<R> executeInsideTransaction(TransactionalOperation<T, Mono<R>> operation, T data) {
        return ReactiveRequestContextHolder.getTransactionalContext()
            .flatMap(context -> operation.apply(data)
                .doOnNext(result -> context.addOperation(operation))
                .onErrorResume(throwable -> handleError(context, throwable)));
    }

    private <R> Mono<R> handleError(TransactionalContext context, Throwable e) {
        TransactionalOperation<?, ?> nextOperation;
        while ((nextOperation = context.popOperation()) != null) {
            nextOperation.revert();
        }

        return Mono.error(new GlobalTransactionException(e));
    }
}
