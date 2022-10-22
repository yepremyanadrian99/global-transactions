package am.adrian.global.transactions.domain;

import java.util.Stack;
import lombok.ToString;

/**
 * The main class that holds context data, which includes the cache of operations.
 */
@ToString
public class TransactionalContext {

    private final Stack<TransactionalOperation<?, ?>> operations = new Stack<>();

    public void addOperation(TransactionalOperation<?, ?> operation) {
        operations.push(operation);
    }

    public TransactionalOperation<?, ?> popOperation() {
        if (operations.empty()) {
            return null;
        }

        return operations.pop();
    }
}
