package com.moongcheap_backend.common.loadtest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/load-tests/internal/orders")
@ConditionalOnProperty(name = "moongcheap.load-test.enabled", havingValue = "true")
public class OrderLoadTestController {
    private final OrderLoadTestService service;
    private final String key;

    public OrderLoadTestController(OrderLoadTestService service,
        @Value("${moongcheap.security.internal-api-key:}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("부하테스트 내부 API 키가 필요합니다.");
        this.service = service;
        this.key = key;
    }

    public record SeedRequest(int groups, int demandsPerGroup) { }

    @PostMapping("/seed")
    public OrderLoadTestService.Manifest seed(
        @RequestHeader(value = "X-Internal-Api-Key", defaultValue = "") String supplied,
        @RequestBody SeedRequest request) {
        authenticate(supplied);
        return service.seed(request.groups(), request.demandsPerGroup());
    }

    @PostMapping("/{runId}/products/{productId}")
    public Map<String, Long> create(
        @RequestHeader(value = "X-Internal-Api-Key", defaultValue = "") String supplied,
        @PathVariable UUID runId, @PathVariable long productId) {
        authenticate(supplied);
        return Map.of("groupBuyId", service.create(runId, productId));
    }

    @PostMapping("/verify")
    public OrderLoadTestService.Report verify(
        @RequestHeader(value = "X-Internal-Api-Key", defaultValue = "") String supplied,
        @RequestBody OrderLoadTestService.Manifest manifest) {
        authenticate(supplied);
        return service.verify(manifest);
    }

    @DeleteMapping("/{runId}")
    public OrderLoadTestService.CleanupReport cleanup(
        @RequestHeader(value = "X-Internal-Api-Key", defaultValue = "") String supplied,
        @PathVariable UUID runId) {
        authenticate(supplied);
        return service.cleanup(runId);
    }

    // 기존 필터의 dev bypass 설정과 무관하게 테스트 진입점의 인증을 유지한다.
    private void authenticate(String supplied) {
        if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
