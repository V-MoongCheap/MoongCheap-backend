package com.moongcheap_backend.common.unit.security.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.common.security.SessionPrincipalResolver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.NativeWebRequest;

class SessionPrincipalResolverSuccessTest {

    private SessionPrincipalResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SessionPrincipalResolver();
    }

    @Nested
    @DisplayName("supportsParameter - 성공")
    class SupportsParameterTest {

        @Test
        void 파라미터_타입이_SessionPrincipal인지_확인한다() {
            MethodParameter parameter = mock(MethodParameter.class);
            doReturn(SessionPrincipal.class).when(parameter).getParameterType();

            assertThat(resolver.supportsParameter(parameter)).isTrue();
        }

        @Test
        void 파라미터_타입이_SessionPrincipal이_아닌_경우_확인한다() {
            MethodParameter parameter = mock(MethodParameter.class);
            doReturn(String.class).when(parameter).getParameterType();

            assertThat(resolver.supportsParameter(parameter)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveArgument - 성공")
    class ResolveArgumentTest {

        @Test
        void 유효한_세션에서_SessionPrincipal을_resolve한다() {
            SessionPrincipal principal = new SessionPrincipal(
                1L, "user1234", "홍길동", Set.of(MemberRole.BUYER), false, true
            );
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(AuthSessionManager.PRINCIPAL_ATTR, principal);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(session);

            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class))
                .thenReturn(request);

            Object result = resolver.resolveArgument(null, null, webRequest, null);

            assertThat(result).isInstanceOf(SessionPrincipal.class);
            assertThat(((SessionPrincipal) result).memberId()).isEqualTo(1L);
        }
    }
}
