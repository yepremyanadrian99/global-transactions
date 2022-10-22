package am.adrian.global.transactions.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The exception class that will be thrown in case any exception happens during the globally transactional execution.
 * The class also wraps the original exception that was thrown to later be retrieved if necessary.
 */
@RequiredArgsConstructor
@Getter
public class GlobalTransactionException extends RuntimeException {

    private final Throwable cause;
}
