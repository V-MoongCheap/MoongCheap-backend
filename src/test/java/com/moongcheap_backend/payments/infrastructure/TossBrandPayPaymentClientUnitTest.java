package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentRequest;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossBrandPayPaymentClientUnitTest {

    @Test
    void 결제를_Basic_인증과_멱등키로_전액_취소한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayPaymentClient client = new TossBrandPayPaymentClient(
            builder.build(), "https://api.tosspayments.com", "test-secret");
        String basic = "Basic " + Base64.getEncoder().encodeToString(
            "test-secret:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(
                "https://api.tosspayments.com/v1/payments/payment-key/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", basic))
            .andExpect(header("Idempotency-Key", "cancel-idempotency-key"))
            .andExpect(content().json("""
                {"cancelReason":"고객 요청"}
                """))
            .andRespond(withSuccess("""
                {
                  "paymentKey": "payment-key",
                  "orderId": "ORDER-100",
                  "status": "CANCELED",
                  "cancels": [{
                    "cancelReason": "고객 요청",
                    "canceledAt": "2026-09-22T15:30:00+09:00",
                    "cancelStatus": "DONE"
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        PaymentCancellationClient.CancellationResponse response = client.cancel(
            "payment-key", "고객 요청", "cancel-idempotency-key");

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.cancelReason()).isEqualTo("고객 요청");
        server.verify();
    }

    @Test
    void 명시적인_NOT_FOUND_PAYMENT만_미조회로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayPaymentClient client = new TossBrandPayPaymentClient(
            builder.build(), "https://api.tosspayments.com", "test-secret");
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/order-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"NOT_FOUND_PAYMENT\"}"));
        assertThat(client.findByOrderId("order-1")).isEmpty();
    }

    @Test
    void 승인거절만_확정실패로_분류한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayPaymentClient client = new TossBrandPayPaymentClient(
            builder.build(), "https://api.tosspayments.com", "test-secret");
        server.expect(requestTo("https://api.tosspayments.com/v1/brandpay/payments"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"REJECT_CARD_COMPANY\"}"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.pay(
            new AutomaticPaymentRequest("customer", "method", PaymentType.CARD,
                1000, "order-1", "상품"), "key"))
            .isInstanceOfSatisfying(PaymentGatewayException.class,
                error -> assertThat(error.kind()).isEqualTo(PaymentGatewayException.Kind.DECLINED));
    }

    @Test
    void 카드_자동결제를_Basic_인증과_멱등키로_실행한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayPaymentClient client = new TossBrandPayPaymentClient(
            builder.build(), "https://api.tosspayments.com", "test-secret");
        String basic = "Basic " + Base64.getEncoder().encodeToString(
            "test-secret:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.tosspayments.com/v1/brandpay/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", basic))
            .andExpect(header("Idempotency-Key", "payment-idempotency-key"))
            .andExpect(content().json("""
                {
                  "customerKey": "Secure_customerKey.1",
                  "methodKey": "card-method-key",
                  "amount": 20000,
                  "orderId": "ORD-automatic-1",
                  "orderName": "공동구매 상품",
                  "cardInstallmentPlan": 0,
                  "taxFreeAmount": 0
                }
                """))
            .andRespond(withSuccess("""
                {
                  "paymentKey": "payment-key",
                  "orderId": "ORD-automatic-1",
                  "orderName": "공동구매 상품",
                  "totalAmount": 20000,
                  "status": "DONE",
                  "approvedAt": "2026-09-21T12:00:00+09:00"
                }
                """, MediaType.APPLICATION_JSON));

        AutomaticPaymentResponse response = client.pay(
            new AutomaticPaymentRequest(
                "Secure_customerKey.1",
                "card-method-key",
                PaymentType.CARD,
                20_000,
                "ORD-automatic-1",
                "공동구매 상품"
            ),
            "payment-idempotency-key"
        );

        assertThat(response.paymentKey()).isEqualTo("payment-key");
        assertThat(response.status()).isEqualTo("DONE");
        server.verify();
    }

    @Test
    void 계좌_자동결제에는_문화비_여부를_전달한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayPaymentClient client = new TossBrandPayPaymentClient(
            builder.build(), "https://api.tosspayments.com", "test-secret");

        server.expect(requestTo("https://api.tosspayments.com/v1/brandpay/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "customerKey": "Secure_customerKey.1",
                  "methodKey": "account-method-key",
                  "amount": 20000,
                  "orderId": "ORD-automatic-1",
                  "orderName": "공동구매 상품",
                  "cultureExpense": false,
                  "taxFreeAmount": 0
                }
                """))
            .andRespond(withSuccess("""
                {
                  "paymentKey": "payment-key",
                  "orderId": "ORD-automatic-1",
                  "orderName": "공동구매 상품",
                  "totalAmount": 20000,
                  "status": "DONE",
                  "approvedAt": "2026-09-21T12:00:00+09:00"
                }
                """, MediaType.APPLICATION_JSON));

        client.pay(
            new AutomaticPaymentRequest(
                "Secure_customerKey.1",
                "account-method-key",
                PaymentType.ACCOUNT,
                20_000,
                "ORD-automatic-1",
                "공동구매 상품"
            ),
            "payment-idempotency-key"
        );

        server.verify();
    }
}
