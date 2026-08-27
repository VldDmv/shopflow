package com.shopflow.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final UserFeignClient feignClient;

    public UserClient(UserFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    // The fallback belongs on the OUTER aspect. Retry runs at order
    // LOWEST_PRECEDENCE-4 and CircuitBreaker at -3, so the chain is
    // Retry(CircuitBreaker(call)). With the fallback on @CircuitBreaker it
    // turned every failure into a successful `true` before Retry could see
    // it, and no retry ever happened. On @Retry the breaker's exception
    // propagates, the retries run, and only then does the fallback fire —
    // except when the breaker is OPEN: CallNotPermittedException is not in
    // retry-exceptions, so that path still fails fast.
    @CircuitBreaker(name = "userClient")
    @Retry(name = "userClient", fallbackMethod = "userExistsFallback")
    public boolean userExists(Long userId) {
        var response = feignClient.getUserById(userId);
        return response != null && response.id() != null;
    }

    @SuppressWarnings("unused")
    private boolean userExistsFallback(Long userId, Throwable ex) {
        log.warn("user-service unavailable for userId={} (cause={}), allowing order in degraded mode",
                userId, ex.getClass().getSimpleName());
        return true;
    }
}