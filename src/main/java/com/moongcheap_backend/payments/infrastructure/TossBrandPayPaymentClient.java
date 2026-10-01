package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentRequest;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.PaymentCancellationClient.CancellationResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static com.moongcheap_backend.payments.infrastructure.PaymentGatewayException.Kind.*;

/** 서버 시크릿 키로 토스 브랜드페이 자동결제 API를 호출한다. */
@Slf4j
@Component
public class TossBrandPayPaymentClient implements BrandPayPaymentClient,
    PaymentReconciliationClient, PaymentCancellationClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final String authorization;

    public TossBrandPayPaymentClient(
        RestClient restClient,
        @Value("${moongcheap.payments.brand-pay.base-url}") String baseUrl,
        @Value("${moongcheap.payments.brand-pay.secret-key:}") String secretKey
    ) {
        this.restClient = restClient.mutate().baseUrl(baseUrl).build();
        this.authorization = createBasicAuthorization(secretKey);
    }

    @Override
    public AutomaticPaymentResponse pay(AutomaticPaymentRequest request,
        String idempotencyKey) {
        validate(request, idempotencyKey);

        Object body = request.paymentType() == PaymentType.CARD
            ? new CardPaymentRequest(
                request.customerKey(),
                request.methodKey(),
                request.amount(),
                request.orderId(),
                request.orderName(),
                0,
                0
            )
            : new AccountPaymentRequest(
                request.customerKey(),
                request.methodKey(),
                request.amount(),
                request.orderId(),
                request.orderName(),
                false,
                0
            );

        try {
            AutomaticPaymentResponse response = restClient.post()
                .uri("/v1/brandpay/payments")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AutomaticPaymentResponse.class);

            if (response == null
                || response.paymentKey() == null
                || response.paymentKey().isBlank()
                || response.approvedAt() == null) {
                throw new PaymentGatewayException(INVALID_RESPONSE, "INVALID_PAYMENT_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw classify(exception, true);
        } catch (RestClientException exception) {
            // 인증 정보, methodKey, 결제 응답 원문은 로그에 기록하지 않는다.
            log.warn("BrandPay automatic payment request failed: orderId={}",
                request.orderId());
            throw new PaymentGatewayException(UNKNOWN, "GATEWAY_TRANSPORT_ERROR");
        }
    }

    @Override
    public Optional<AutomaticPaymentResponse> findByOrderId(String orderId) {
        if (authorization == null) {
            throw new PaymentGatewayException(CONFIGURATION, "MISSING_SECRET_KEY");
        }
        try {
            AutomaticPaymentResponse response = restClient.get()
                .uri("/v1/payments/orders/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve().body(AutomaticPaymentResponse.class);
            if (response == null) {
                throw new PaymentGatewayException(INVALID_RESPONSE, "EMPTY_LOOKUP_RESPONSE");
            }
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404
                && "NOT_FOUND_PAYMENT".equals(errorCode(exception))) return Optional.empty();
            throw classify(exception, false);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException(UNKNOWN, "LOOKUP_TRANSPORT_ERROR");
        }
    }

    /**
     * 코어 결제 취소 API로 전액 취소한다. cancelAmount를 보내지 않으면 토스가 전액
     * 취소로 처리한다. 호출자가 만든 멱등키를 그대로 전달해 네트워크 재시도 시의
     * 중복 취소를 방지한다.
     */
    @Override
    public CancellationResponse cancel(String paymentKey, String cancelReason,
        String idempotencyKey) {
        validateCancellation(paymentKey, cancelReason, idempotencyKey);

        try {
            TossCancellationResponse response = restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancellationRequest(cancelReason))
                .retrieve()
                .body(TossCancellationResponse.class);

            CancelDetail completed = response == null || response.cancels() == null
                ? null
                : response.cancels().stream()
                    .filter(cancel -> "DONE".equals(cancel.cancelStatus()))
                    .filter(cancel -> cancel.canceledAt() != null)
                    .max(java.util.Comparator.comparing(CancelDetail::canceledAt))
                    .orElse(null);

            if (response == null
                || response.paymentKey() == null || response.paymentKey().isBlank()
                || response.orderId() == null || response.orderId().isBlank()
                || !"CANCELED".equals(response.status())
                || completed == null || completed.canceledAt() == null
                || completed.cancelReason() == null || completed.cancelReason().isBlank()) {
                throw new PaymentGatewayException(INVALID_RESPONSE,
                    "INVALID_CANCELLATION_RESPONSE");
            }

            return new CancellationResponse(response.paymentKey(), response.orderId(),
                response.status(), completed.canceledAt(), completed.cancelReason());
        } catch (RestClientResponseException exception) {
            throw classifyCancellation(exception);
        } catch (RestClientException exception) {
            log.warn("Payment cancellation request failed");
            throw new PaymentGatewayException(UNKNOWN,
                "CANCELLATION_TRANSPORT_ERROR");
        }
    }

    private PaymentGatewayException classify(RestClientResponseException exception,
        boolean paymentRequest) {
        String code = errorCode(exception);
        if (paymentRequest && exception.getStatusCode().is4xxClientError()
            && Set.of("REJECT_CARD_COMPANY", "REJECT_ACCOUNT_PAYMENT").contains(code)) {
            return new PaymentGatewayException(DECLINED, code);
        }
        if (Set.of("UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT").contains(code)) {
            return new PaymentGatewayException(CONFIGURATION, code);
        }
        return new PaymentGatewayException(UNKNOWN, code);
    }

    private PaymentGatewayException classifyCancellation(
        RestClientResponseException exception) {
        String code = errorCode(exception);
        if (Set.of("NOT_CANCELABLE_PAYMENT", "ALREADY_CANCELED_PAYMENT")
            .contains(code)) {
            return new PaymentGatewayException(DECLINED, code);
        }
        if (Set.of("UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT").contains(code)) {
            return new PaymentGatewayException(CONFIGURATION, code);
        }
        return new PaymentGatewayException(UNKNOWN, code);
    }

    private String errorCode(RestClientResponseException exception) {
        try {
            GatewayError error = exception.getResponseBodyAs(GatewayError.class);
            if (error != null && error.code() != null
                && error.code().matches("[A-Z0-9_]{1,100}")) return error.code();
        } catch (RuntimeException ignored) {
            // 응답 원문은 로그에 남기지 않는다.
        }
        return "HTTP_" + exception.getStatusCode().value();
    }

    private record GatewayError(String code) {}

    private void validate(AutomaticPaymentRequest request, String idempotencyKey) {
        if (authorization == null) {
            throw new PaymentGatewayException(CONFIGURATION, "MISSING_SECRET_KEY");
        }
        if (request == null
            || request.customerKey() == null || request.customerKey().isBlank()
            || request.methodKey() == null || request.methodKey().isBlank()
            || request.paymentType() == null
            || request.amount() <= 0
            || request.orderId() == null || request.orderId().isBlank()
            || request.orderName() == null || request.orderName().isBlank()
            || request.orderName().length() > 100
            || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentGatewayException(CONFIGURATION, "INVALID_REQUEST_SNAPSHOT");
        }
    }

    private void validateCancellation(String paymentKey, String cancelReason,
        String idempotencyKey) {
        if (authorization == null) {
            throw new PaymentGatewayException(CONFIGURATION, "MISSING_SECRET_KEY");
        }
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200
            || cancelReason == null || cancelReason.isBlank() || cancelReason.length() > 200
            || idempotencyKey == null || idempotencyKey.isBlank()
            || idempotencyKey.length() > 300) {
            throw new PaymentGatewayException(CONFIGURATION,
                "INVALID_CANCELLATION_REQUEST");
        }
    }

    private String createBasicAuthorization(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }
        String credentials = Base64.getEncoder().encodeToString(
            (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + credentials;
    }

    /** 카드 자동결제에는 할부 개월 수를 0(일시불)로 명시한다. */
    private record CardPaymentRequest(
        String customerKey,
        String methodKey,
        int amount,
        String orderId,
        String orderName,
        int cardInstallmentPlan,
        int taxFreeAmount
    ) {
    }

    /** 계좌 자동결제에는 계좌 전용 필수값인 문화비 여부를 명시한다. */
    private record AccountPaymentRequest(
        String customerKey,
        String methodKey,
        int amount,
        String orderId,
        String orderName,
        boolean cultureExpense,
        int taxFreeAmount
    ) {
    }

    private record CancellationRequest(String cancelReason) {
    }

    private record TossCancellationResponse(
        String paymentKey,
        String orderId,
        String status,
        List<CancelDetail> cancels
    ) {
    }

    private record CancelDetail(
        String cancelReason,
        OffsetDateTime canceledAt,
        String cancelStatus
    ) {
    }
}
