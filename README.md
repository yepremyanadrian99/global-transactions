# Getting Started

## Installation

* Add valid repository-scoped token inside your `gradle.properties` file under `githubAuthToken` key.
* Add the dependency.

```groovy
implementation 'am.adrianyepremyan:global-transactions:1.1.0'
```

## Configuration and Usage

### Blocking application

<p>
Instance of class <code>TransactionalOperationExecutor</code> should be created and used to wrap regular method calls 
within a transaction.
</p>
<p>
Below is a Spring Boot configuration demonstrating the bean creation.
</p>

```java
import am.adrianyepremyan.global.transactions.service.TransactionalOperationExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalTransactionsConfiguration {

    @Bean
    public TransactionalOperationExecutor transactionalOperationExecutor() {
        return new TransactionalOperationExecutor();
    }
}
```

<p>
To wrap a unit of business logic or operation with a transaction `TransactionalOperation` should be implemented by
overriding the two abstract methods <code>apply</code> and <code>revert</code>, the former of which should contain
the business logic, e.g. an external API call, and the latter should contain the reverting logic, e.g. an external 
API call with an opposite effect towards the former so that any side effects are cancelled.
</p>

```java
import am.adrianyepremyan.global.transactions.domain.TransactionalOperation;
import am.adrianyepremyan.global.transactions.service.TransactionalOperationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public record UserService(
    TransactionalOperationExecutor executor,
    RestTemplate restTemplate
) {

    public FolderCreateRequest findOrCreateUser(FolderCreateRequest request) {
        return executor.executeInsideTransaction(findOrCreateUserOperation(), request);
    }

    private TransactionalOperation<FolderCreateRequest, FolderCreateRequest> findOrCreateUserOperation() {
        return new TransactionalOperation<>() {
            // This will store the request done for user creation
            // to be later used for reverting if needed.
            private UserCreateRequest cachedUserRequest;

            @Override
            public FolderCreateRequest apply(FolderCreateRequest request) {
                try {
                    // Finding the user.
                    // If the user is not found, 404 will be returned and exception will be thrown.
                    restTemplate.getForObject(buildFindUserUri(request.userId()), UserResponse.class);
                } catch (Throwable ignored1) {
                    // The user is not found, so will create one now.
                    final var createRequest = new UserCreateRequest(
                        request.userId()
                    );
                    restTemplate.postForObject(buildCreateUserUri(), createRequest, UserResponse.class);
                    // Cache the user creation request.
                    cachedUserRequest = createRequest;
                }
                return request;
            }

            @Override
            public void revert() {
                if (cachedUserRequest == null) {
                    // User was not cached, so nothing to revert.
                    return;
                }

                final var userId = cachedUserRequest.userId();
                // An API call for reverting the create user request
                // which is a DELETE request.
                restTemplate.delete(buildDeleteUserUri(userId));

                // Finished reverting the create user request.
            }
        };
    }
}
```

<p>
If at some point an error happens, the <code>revert</code> method will get triggered, the user will be deleted 
if such was created earlier and a <code>GlobalTransactionException</code> will be thrown to the initial caller.
</p>
<p> 
Several transactional service method calls can be made within a single transactional method, in which case 
all transactional operations should provide a reverse logic except for the very first called one.
The reason is that the very first transactional operation could have only been added to the stack of executed ones
in case there were no errors in the upcoming method executions, which is always false in case any revert operation 
is called, and if there are no errors, then the first transactional operation will just complete normally 
without needing any reverse logic.
</p>

```java
import am.adrianyepremyan.global.transactions.domain.TransactionalOperation;
import am.adrianyepremyan.global.transactions.service.TransactionalOperationExecutor;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public record FolderService(
    UserService userService,
    ValidationService validationService,
    TransactionalOperationExecutor executor,
    FolderRepository repository
) {

    public FolderCreateResponse create(FolderCreateRequest request) {
        return executor.executeInsideTransaction(createOperation(), request);
    }

    private TransactionalOperation<FolderCreateRequest, FolderCreateResponse> createOperation() {
        return new TransactionalOperation<>() {
            @Override
            public FolderCreateResponse apply(FolderCreateRequest request) {
                return Optional.of(request)
                    // Makes the first API call to an external service.
                    .map(userService::findOrCreateUser)
                    // Makes the second API call to another external service.
                    .map(validationService::validate)
                    // Makes a call to the underlying DB.
                    .map(repository::save)
                    // For demonstration purposes.
                    .orElseThrow(
                        () -> new RuntimeException("This exception will never get thrown, unless the request is null")
                    );
            }

            @Override
            public void revert() {
                // This method should never get called as it will never be added to the stack of executed operations
                // if any other transactional method fails.
                throw new UnsupportedOperationException("This should never be thrown");
            }
        };
    }
}
```

### Reactive application

<p>
Instance of class <code>ReactiveTransactionalOperationExecutor</code> should be created and used to wrap regular method 
calls within a transaction.
</p>
<p>
Below is a Spring Boot configuration demonstrating the bean creation.
</p>

```java
import am.adrianyepremyan.global.transactions.service.ReactiveTransactionalOperationExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalTransactionsConfig {

    @Bean
    public ReactiveTransactionalOperationExecutor reactiveTransactionalOperationExecutor() {
        return new ReactiveTransactionalOperationExecutor();
    }
}
```

<p>
Unlike the simple transactional executor instance configuration requirement in case of blocking / non-reactive 
application, a WebFilter needs to also be created to successfully configure the transactional context,
as in reactive applications a thread-bound context cannot be used and an application-wise context should be used 
instead, which can be done using <code>Mono.contextWrite()</code> method as demonstrated below.
</p>

```java
import am.adrianyepremyan.global.transactions.constant.ContextConstants;
import am.adrianyepremyan.global.transactions.domain.TransactionalContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filter class responsible for creating an empty transactional context on each request.
 * This context will later hold all the required information regarding transactional operations, etc.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRequestContextFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Initiating an empty transactional context.
        return chain.filter(exchange)
            .contextWrite(context -> context.put(ContextConstants.TRANSACTIONAL_CONTEXT, new TransactionalContext()));
    }
}
```

<p>
The built-in <code>ContextConstants.TRANSACTIONAL_CONTEXT</code> should be used for configuration as a key for the 
library-specific data storage inside the reactive context as shown above.
</p>

<p>
To wrap a unit of business logic or operation with a transaction `TransactionalOperation` should be implemented by
overriding the two abstract methods `apply` and `revert`, the former of which should contain the business logic, e.g. an
external API call, and the latter should contain the reverting logic, e.g. an external API call with an opposite effect
towards the former so that any side effects are cancelled. 
</p>

```java
import am.adrianyepremyan.global.transactions.domain.TransactionalOperation;
import am.adrianyepremyan.global.transactions.service.ReactiveTransactionalOperationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public record UserService(
    ReactiveTransactionalOperationExecutor executor,
    WebClient userServiceClient
) {

    public Mono<FolderCreateRequest> findOrCreateUser(FolderCreateRequest request) {
        return executor.executeInsideTransaction(findOrCreateUserOperation(), request);
    }

    private TransactionalOperation<FolderCreateRequest, Mono<FolderCreateRequest>> findOrCreateUserOperation() {
        return new TransactionalOperation<>() {
            private UserCreateRequest cachedUserRequest;

            @Override
            public Mono<FolderCreateRequest> apply(FolderCreateRequest request) {
                return userServiceClient.get()
                    // Finding the user.
                    // If the user is not found, 404 will be returned
                    // and Mono.error() will be propagated downstream.
                    .uri((builder) -> buildInternalFindUserUri(builder, request.userId()))
                    .retrieve()
                    .bodyToMono(UserResponse.class)
                    .onErrorResume(throwable -> {
                        final var createRequest = new UserCreateRequest(
                            request.userId(),
                            request.name(),
                            request.description()
                        );
                        // The user is not found, so will create one now.
                        final var result = userServiceClient.post()
                            .uri(UriUtil::buildInternalCreateUserUri)
                            .body(BodyInserters.fromValue(createRequest))
                            .retrieve()
                            .bodyToMono(UserResponse.class);
                        // Cache the request if it was successful
                        cachedUserRequest = createRequest;
                        log.info("User create request is cached");
                        return result;
                    })
                    .thenReturn(request);
            }

            @Override
            public void revert() {
                if (cachedUserRequest == null) {
                    // User was not cached, so nothing to revert
                    return;
                }

                final var userId = cachedUserRequest.userId();
                // An API call for reverting the create user request
                // which is a DELETE request.
                userServiceClient.delete()
                    .uri(builder -> UriUtil.buildInternalDeleteUserUri(builder, userId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
                // Finished reverting the create user request.
            }
        };
    }
}
```

<p>
If at some point an error happens, the <code>revert</code> method will get triggered, the user will be deleted
if such was created earlier and a <code>Mono.error()</code> with internal <code>GlobalTransactionException</code> 
will be propagated downstream to the initial caller.
</p>
<p> 
Several transactional service method calls can be made within a single transactional method, in which case all 
transactional operations should provide a reverse logic except for the very first called one.
The reason is that the very first transactional operation could have only been added to the stack of executed ones 
in case there were no errors in the upcoming method executions, which is always false in case any revert operation
is called, and if there are no errors, then the first transactional operation will just complete normally
without needing any reverse logic.
</p>

```java
import am.adrianyepremyan.global.transactions.domain.TransactionalOperation;
import am.adrianyepremyan.global.transactions.service.ReactiveTransactionalOperationExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public record FolderService(
    UserService userService,
    ValidationService validationService,
    ReactiveTransactionalOperationExecutor executor,
    FolderRepository repository
) {

    public Mono<FolderCreateResponse> create(FolderCreateRequest request) {
        return executor.executeInsideTransaction(createOperation(), request);
    }

    private TransactionalOperation<FolderCreateRequest, Mono<FolderCreateResponse>> createOperation() {
        return new TransactionalOperation<>() {
            @Override
            public Mono<FolderCreateResponse> apply(FolderCreateRequest request) {
                return Mono.just(request)
                    // Makes the first asynchronous API call to an external service.
                    .flatMap(userService::findOrCreateUser)
                    // Makes the second asynchronous API call to another external service.
                    .flatMap(validationService::validate)
                    // Makes an asynchronous call to the underlying DB.
                    .flatMap(repository::save)
                    .subscribeOn(Schedulers.boundedElastic());
            }

            @Override
            public void revert() {
                throw new UnsupportedOperationException("This should never be thrown");
            }
        };
    }
}
```

# Useful links

* ### Blocking application repository
  https://github.com/yepremyanadrian99/global-transactions-requester

* ### Reactive application repository
  https://github.com/yepremyanadrian99/global-transactions-reactive-requester
