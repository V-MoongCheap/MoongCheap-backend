package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class PaymentGatewayConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentGatewayProperties.class)
    static class PropertiesConfiguration { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration.class,
            TossBrandPayPaymentClient.class, MockBrandPayPaymentClient.class)
        .withBean(RestClient.class, () -> RestClient.builder().build())
        .withPropertyValues("moongcheap.payments.brand-pay.base-url=https://api.tosspayments.com");

    @Test void defaultUsesOnlyToss() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(BrandPayPaymentClient.class);
            assertThat(context.getBean(BrandPayPaymentClient.class))
                .isInstanceOf(TossBrandPayPaymentClient.class);
            assertThat(context).doesNotHaveBean(MockBrandPayPaymentClient.class);
        });
    }

    @Test void mockReplacesAllThreeGatewayInterfaces() {
        runner.withPropertyValues("moongcheap.payments.gateway.mode=mock",
            "moongcheap.payments.gateway.mock-delay=3s").run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(TossBrandPayPaymentClient.class)
                    .hasSingleBean(BrandPayPaymentClient.class)
                    .hasSingleBean(PaymentReconciliationClient.class)
                    .hasSingleBean(PaymentCancellationClient.class);
                var mock = context.getBean(MockBrandPayPaymentClient.class);
                assertThat(context.getBean(BrandPayPaymentClient.class)).isSameAs(mock);
                assertThat(context.getBean(PaymentReconciliationClient.class)).isSameAs(mock);
                assertThat(context.getBean(PaymentCancellationClient.class)).isSameAs(mock);
                assertThat(context.getBean(PaymentGatewayProperties.class).getMockDelay())
                    .isEqualTo(java.time.Duration.ofSeconds(3));
            });
    }

    @Test void invalidModeFailsStartup() {
        runner.withPropertyValues("moongcheap.payments.gateway.mode=typo")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidDelayFailsStartup() {
        runner.withPropertyValues("moongcheap.payments.gateway.mode=mock",
            "moongcheap.payments.gateway.mock-delay=-1ms")
            .run(context -> assertThat(context).hasFailed());
    }
}
