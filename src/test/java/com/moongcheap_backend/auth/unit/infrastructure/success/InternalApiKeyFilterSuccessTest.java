package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

class InternalApiKeyFilterSuccessTest {

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filter, "internalApiKey", "secret-key");
    }

    @Nested
    @DisplayName("shouldNotFilter - 성공")
    class ShouldNotFilterTest {

        @Test
        void INTERNAL_PATTERNS에_매치되지_않는_일반_경로로_요청이_들어온다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/products");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            // shouldNotFilter=true → 필터 건너뜀 → filterChain.doFilter 직접 호출
            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        void api_awarding_하위_경로로_요청이_들어온다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/awarding/result");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            // shouldNotFilter=false → doFilterInternal 실행 → 키 없음 → 401
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("doFilterInternal - 성공")
    class DoFilterInternalTest {

        @Test
        void 올바른_X_Internal_Api_Key_헤더로_internal_경로를_요청한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/awarding/result");
            request.addHeader("X-Internal-Api-Key", "secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
