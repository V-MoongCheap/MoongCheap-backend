package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class AuthSessionManagerSuccessTest {

    @Mock SessionRepository sessionRepository;
    @Mock FindByIndexNameSessionRepository indexRepository;
    @Mock StringRedisTemplate redisTemplate;

    AuthSessionManager authSessionManager;

    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        authSessionManager = new AuthSessionManager(sessionRepository, indexRepository, redisTemplate);
        ReflectionTestUtils.setField(authSessionManager, "defaultTtl", Duration.ofHours(24));
        ReflectionTestUtils.setField(authSessionManager, "rememberMeTtl", Duration.ofDays(14));
        principal = new SessionPrincipal(1L, "user1234", "닉네임", Set.of(MemberRole.BUYER), false, true);
    }

    @Nested
    @DisplayName("bindPrincipal - 성공")
    class BindPrincipalTest {

        @Test
        void 기존_세션_없이_principal을_바인딩한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            authSessionManager.bindPrincipal(request, principal, false);

            assertThat(request.getSession(false)).isNotNull();
            assertThat(request.getSession(false).getAttribute(AuthSessionManager.PRINCIPAL_ATTR))
                .isEqualTo(principal);
        }

        @Test
        void 기존_세션이_있는_상태에서_principal을_바인딩한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpSession originalSession = new MockHttpSession();
            request.setSession(originalSession);

            authSessionManager.bindPrincipal(request, principal, false);

            assertThat(originalSession.isInvalid()).isTrue();
            assertThat(request.getSession(false)).isNotNull();
            assertThat(request.getSession(false).getAttribute(AuthSessionManager.PRINCIPAL_ATTR))
                .isEqualTo(principal);
        }
    }

    @Nested
    @DisplayName("refreshPrincipal - 성공")
    class RefreshPrincipalTest {

        @Test
        void 세션이_존재하는_상태에서_principal을_갱신한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true);

            authSessionManager.refreshPrincipal(request, principal);

            assertThat(request.getSession(false).getAttribute(AuthSessionManager.PRINCIPAL_ATTR))
                .isEqualTo(principal);
        }

        @Test
        void 세션이_없는_상태에서_principal을_갱신한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            assertThatCode(() -> authSessionManager.refreshPrincipal(request, principal))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("invalidateAllForMember - 성공")
    class InvalidateAllForMemberTest {

        @Test
        @SuppressWarnings("unchecked")
        void 사용자의_세션이_존재하는_상태로_세션을_무효화_한다() {
            Map<String, Session> sessions = Map.of("session-id-1", mock(Session.class));
            when(indexRepository.findByPrincipalName("1")).thenReturn(sessions);

            authSessionManager.invalidateAllForMember(1L);

            verify(sessionRepository).deleteById("session-id-1");
        }

        @Test
        @SuppressWarnings("unchecked")
        void 사용자의_세션이_존재하지_않는_상태로_세션을_무효화_한다() {
            when(indexRepository.findByPrincipalName("1")).thenReturn(Map.of());

            authSessionManager.invalidateAllForMember(1L);

            verify(sessionRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("invalidateCurrent - 성공")
    class InvalidateCurrentTest {

        @Test
        void 세션이_존재하는_상태에서_현재_세션을_무효화한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpSession session = new MockHttpSession();
            request.setSession(session);

            authSessionManager.invalidateCurrent(request);

            assertThat(session.isInvalid()).isTrue();
        }
    }

    @Nested
    @DisplayName("invalidateAllExceptCurrent - 성공")
    class InvalidateAllExceptCurrentTest {

        @Test
        @SuppressWarnings("unchecked")
        void 다른_기기_세션이_존재하는_상태에서_현재_세션을_제외한_나머지를_무효화한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String currentId = request.getSession(true).getId();
            String otherId = "other-session-id";
            Session currentSession = mock(Session.class);
            Session otherSession = mock(Session.class);
            Map<String, Session> sessions = Map.of(currentId, currentSession, otherId, otherSession);
            when(indexRepository.findByPrincipalName("1")).thenReturn(sessions);

            authSessionManager.invalidateAllExceptCurrent(1L, request);

            verify(sessionRepository).deleteById(otherId);
            verify(sessionRepository, never()).deleteById(currentId);
        }
    }
}
