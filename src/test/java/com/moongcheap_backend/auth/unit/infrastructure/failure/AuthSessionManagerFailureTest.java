package com.moongcheap_backend.auth.unit.infrastructure.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class AuthSessionManagerFailureTest {

    @Mock SessionRepository sessionRepository;
    @Mock FindByIndexNameSessionRepository indexRepository;
    @Mock StringRedisTemplate redisTemplate;

    @InjectMocks
    AuthSessionManager authSessionManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authSessionManager, "defaultTtl", Duration.ofHours(24));
        ReflectionTestUtils.setField(authSessionManager, "rememberMeTtl", Duration.ofDays(14));
    }

    @Nested
    @DisplayName("invalidateAllExceptCurrent - 실패")
    class InvalidateAllExceptCurrentFailureTest {

        @Test
        void 세션이_없는_상태에서_현재_세션을_제외한_나머지를_무효화한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            // 세션 생성하지 않음

            assertThatThrownBy(() -> authSessionManager.invalidateAllExceptCurrent(1L, request))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
