package am.adrian.global.transactions.domain;

/**
 * Main interface for all transactional operation classes to implement.
 * The apply function is responsible for keeping the main logic that should be wrapped in a global transaction.
 * The revert function is responsible for keeping the reverse logic of the apply function.
 * <p>
 * Whenever an exception happens during the execution of the apply function,
 * the try-catch statement that wraps the aforementioned function call will execute the revert function,
 * which should logically be equal to the reverse of the apply function's logic.
 *
 * @param <T> the generic type for the input (request) of the apply function.
 * @param <R> the generic type for the output (response) of the apply function.
 */
public interface TransactionalOperation<T, R> {

    R apply(T data);

    void revert();
}
