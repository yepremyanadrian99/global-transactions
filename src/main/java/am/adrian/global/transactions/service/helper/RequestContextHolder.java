package am.adrian.global.transactions.service.helper;

import am.adrian.global.transactions.domain.TransactionalContext;

/**
 * Helper class that accomplishes the job of retrieving the context bound to the current thread.
 */
public class RequestContextHolder {

    private static final ThreadLocal<TransactionalContext> context = ThreadLocal.withInitial(TransactionalContext::new);

    public static TransactionalContext getTransactionalContext() {
        return context.get();
    }
}
