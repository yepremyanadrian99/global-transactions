package am.adrian.global.transactions.service.helper;

import static am.adrian.global.transactions.constant.ContextConstants.TRANSACTIONAL_CONTEXT;

import am.adrian.global.transactions.domain.TransactionalContext;
import reactor.core.publisher.Mono;

/**
 * Helper class that accomplishes the job of retrieving the current reactive requests' context.
 */
public class ReactiveRequestContextHolder {

    public static Mono<TransactionalContext> getTransactionalContext() {
        return Mono.deferContextual(Mono::just)
            .map(contextView -> contextView.get(TRANSACTIONAL_CONTEXT));
    }
}
