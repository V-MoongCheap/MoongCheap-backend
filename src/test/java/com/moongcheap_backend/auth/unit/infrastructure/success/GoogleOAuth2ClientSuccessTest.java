package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleOAuth2ClientSuccessTest {

    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GoogleOAuth2Client client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuth2Client(builder.build());
    }

    @Nested
    @DisplayName("revoke - 성공 처리")
    class RevokeSuccessTest {

        @Test
        void 상태_200이면_정상_종료한다() {
            server.expect(requestTo(REVOKE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

            client.revoke("valid-token");

            server.verify();
        }

        @Test
        void 상태_400은_이미_revoke된_토큰으로_간주해_성공_처리한다() {
            server.expect(requestTo(REVOKE_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatCode(() -> client.revoke("already-invalid"))
                .doesNotThrowAnyException();

            server.verify();
        }

        @Test
        void 상태_404는_존재하지_않는_토큰으로_간주해_성공_처리한다() {
            server.expect(requestTo(REVOKE_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatCode(() -> client.revoke("missing-token"))
                .doesNotThrowAnyException();

            server.verify();
        }
    }

    @Nested
    @DisplayName("revoke - 예외 전파")
    class RevokeErrorTest {

        @Test
        void 상태_401은_예외를_전파해_워커가_재시도하도록_한다() {
            server.expect(requestTo(REVOKE_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> client.revoke("valid-token"))
                .isInstanceOf(Exception.class);

            server.verify();
        }

        @Test
        void 상태_5xx는_예외를_전파해_워커가_재시도하도록_한다() {
            server.expect(requestTo(REVOKE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> client.revoke("valid-token"))
                .isInstanceOf(Exception.class);

            server.verify();
        }
    }

    @Nested
    @DisplayName("revoke - token 없음")
    class RevokeNoTokenTest {

        @Test
        void token이_null이면_요청을_보내지_않는다() {
            RestClient mockRestClient = mock(RestClient.class);
            GoogleOAuth2Client nullTokenClient = new GoogleOAuth2Client(mockRestClient);

            nullTokenClient.revoke(null);

            verify(mockRestClient, never()).post();
        }

        @Test
        void token이_빈_문자열이면_요청을_보내지_않는다() {
            RestClient mockRestClient = mock(RestClient.class);
            GoogleOAuth2Client blankTokenClient = new GoogleOAuth2Client(mockRestClient);

            blankTokenClient.revoke("   ");

            verify(mockRestClient, never()).post();
        }
    }
}
