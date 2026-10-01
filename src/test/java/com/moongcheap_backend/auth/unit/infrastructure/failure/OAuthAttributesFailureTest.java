package com.moongcheap_backend.auth.unit.infrastructure.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.infrastructure.oauth.OAuthAttributes;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OAuthAttributesFailureTest {

    @Nested
    @DisplayName("of - 실패")
    class OfFailureTest {

        @Test
        void 지원하지_않는_provider_registrationId로_파싱한다() {
            assertThatThrownBy(() -> OAuthAttributes.of("naver", Map.of("id", "123")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
