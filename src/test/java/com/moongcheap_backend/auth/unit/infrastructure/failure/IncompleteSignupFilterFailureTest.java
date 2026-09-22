package com.moongcheap_backend.auth.unit.infrastructure.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moongcheap_backend.auth.infrastructure.IncompleteSignupFilter;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class IncompleteSignupFilterFailureTest {

    private IncompleteSignupFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IncompleteSignupFilter();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilterInternal - 실패")
    class DoFilterInternalFailureTest {

        @Test
        void 약관_미동의_사용자가_허용되지_않은_경로로_요청한다() throws Exception {
            SessionPrincipal principal = new SessionPrincipal(
                1L, null, "닉네임", Set.of(MemberRole.BUYER), false, false // termsAgreed=false
            );
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/demands");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus())
                .isEqualTo(ErrorCode.SOCIAL_SIGNUP_INCOMPLETE.getStatus().value());
            assertThat(response.getContentAsString())
                .contains(ErrorCode.SOCIAL_SIGNUP_INCOMPLETE.getCode());
            verify(filterChain, never()).doFilter(request, response);
        }
    }
}
