package com.moongcheap_backend.auth.unit.infrastructure.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moongcheap_backend.auth.infrastructure.InternalApiKeyFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class InternalApiKeyFilterFailureTest {

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filter, "internalApiKey", "secret-key");
    }

    @Nested
    @DisplayName("doFilterInternal - 실패")
    class DoFilterInternalFailureTest {

        @Test
        void X_Internal_Api_Key_헤더_없이_internal_경로를_요청한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/awarding/result");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void 잘못된_X_Internal_Api_Key_헤더로_internal_경로를_요청한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/awarding/result");
            request.addHeader("X-Internal-Api-Key", "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void internalApiKey가_설정되지_않은_상태에서_internal_경로를_요청한다() throws Exception {
            InternalApiKeyFilter blankKeyFilter = new InternalApiKeyFilter();
            ReflectionTestUtils.setField(blankKeyFilter, "internalApiKey", "");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/awarding/result");
            request.addHeader("X-Internal-Api-Key", "any-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            blankKeyFilter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(filterChain, never()).doFilter(request, response);
        }
    }
}
