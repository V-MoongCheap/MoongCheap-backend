package com.moongcheap_backend.common.unit.security.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.NativeWebRequest;

class SessionPrincipalResolverFailureTest {

    private SessionPrincipalResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SessionPrincipalResolver();
    }

    @Nested
    @DisplayName("resolveArgument - 실패")
    class ResolveArgumentTest {

        @Test
        void HttpServletRequest가_없는_상태에서_SessionPrincipal을_resolve한다() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class))
                .thenReturn(null);

            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        }

        @Test
        void 세션이_없는_상태에서_SessionPrincipal을_resolve한다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            // setSession 미호출 → getSession(false) == null

            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class))
                .thenReturn(request);

            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        }

        @Test
        void 세션은_있지만_principal_속성이_없는_상태에서_resolve한다() {
            MockHttpSession session = new MockHttpSession();
            // PRINCIPAL_ATTR 미설정 → getAttribute 결과 null
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(session);

            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class))
                .thenReturn(request);

            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        }

        @Test
        void principal_속성이_SessionPrincipal_타입이_아닌_상태에서_resolve한다() {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(AuthSessionManager.PRINCIPAL_ATTR, "invalid-principal-type");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(session);

            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class))
                .thenReturn(request);

            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        }
    }
}
