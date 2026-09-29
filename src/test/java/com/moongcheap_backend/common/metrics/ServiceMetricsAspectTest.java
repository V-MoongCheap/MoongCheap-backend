package com.moongcheap_backend.common.metrics;

import static org.assertj.core.api.Assertions.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Service;

class ServiceMetricsAspectTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private <T> T proxy(T target) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new ServiceMetricsAspect(registry));
        return factory.getProxy();
    }

    @Test void recordsPublicServiceReturnAndPreservesOriginalFailure() {
        ExampleService target = new ExampleService();
        ExampleService service = proxy(target);
        assertThat(service.work()).isEqualTo("done");
        assertThatThrownBy(service::fail).isSameAs(target.failure);
        assertThat(registry.get("moongcheap.service.execution")
            .tags("service", ExampleService.class.getName(), "method", "work", "outcome", "returned")
            .timer().count()).isEqualTo(1);
        assertThat(registry.get("moongcheap.service.execution")
            .tags("method", "fail", "outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test void separatesNestedServicesButDoesNotDoubleCountSelfInvocation() {
        ExampleService inner = proxy(new ExampleService());
        OuterService outer = proxy(new OuterService(inner));
        assertThat(outer.work()).isEqualTo("done");
        assertThat(registry.get("moongcheap.service.execution")
            .tags("service", OuterService.class.getName(), "method", "work").timer().count()).isEqualTo(1);
        assertThat(registry.get("moongcheap.service.execution")
            .tags("service", ExampleService.class.getName(), "method", "work").timer().count()).isEqualTo(1);
        assertThat(registry.find("moongcheap.service.execution").tag("method", "helper").timer()).isNull();
    }

    @Test void doesNotInstrumentNonServiceComponents() {
        PlainComponent plain = proxy(new PlainComponent());
        assertThat(plain.work()).isEqualTo("plain");
        assertThat(registry.find("moongcheap.service.execution").timer()).isNull();
    }

    @Service
    static class ExampleService {
        final IllegalStateException failure = new IllegalStateException("test");
        public String work() { return helper(); }
        public String helper() { return "done"; }
        public String fail() { throw failure; }
    }

    @Service
    static class OuterService {
        private final ExampleService inner;
        OuterService(ExampleService inner) { this.inner = inner; }
        public String work() { return inner.work(); }
    }

    static class PlainComponent {
        public String work() { return "plain"; }
    }
}
