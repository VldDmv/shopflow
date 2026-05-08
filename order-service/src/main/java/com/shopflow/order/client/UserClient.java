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

    @CircuitBreaker(name = "userClient", fallbackMethod = "userExistsFallback")
    @Retry(name = "userClient")
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