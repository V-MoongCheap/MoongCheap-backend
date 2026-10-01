package com.moongcheap_backend.common.config;

import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RetryLoggingConfig {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerListeners() {
        retryRegistry.getEventPublisher()
            .onEntryAdded(entryEvent -> {
                var retry = entryEvent.getAddedEntry();
                retry.getEventPublisher()
                    .onRetry(event -> log.warn(
                        "Retry attempt: name={}, attempt={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable()))
                    .onError(event -> log.error(
                        "Retry exhausted: name={}, attempts={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable()))
                    .onIgnoredError(event -> log.error(
                        "Retry non-retryable error: name={}",
                        event.getName(),
                        event.getLastThrowable()))
                    .onSuccess(event -> log.info(
                        "Retry succeeded after failures: name={}, attempts={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts()));
            });
    }
}
