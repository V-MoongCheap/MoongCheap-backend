package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuth2ClientSuccessTest {

    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
    private static final String ADMIN_KEY = "test-admin-key";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoOAuth2Client client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuth2Client(builder.build(), ADMIN_KEY);
    }

    @Nested
    @DisplayName("unlink - 성공 처리")
    class UnlinkSuccessTest {

        @Test
        void 상태_200이면_정상_종료한다() {
            server.expect(requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK " + ADMIN_KEY))
                .andRespond(withSuccess());

            client.unlink("kakao-provider-id");

            server.verify();
        }

        @Test
        void 상태_400은_이미_unlink된_사용자로_간주해_성공_처리한다() {
            server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatCode(() -> client.unlink("kakao-provider-id"))
                .doesNotThrowAnyException();

            server.verify();
        }

        @Test
        void 상태_404는_존재하지_않는_target으로_간주해_성공_처리한다() {
            server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatCode(() -> client.unlink("kakao-provider-id"))
                .doesNotThrowAnyException();

            server.verify();
        }
    }

    @Nested
    @DisplayName("unlink - 예외 전파")
    class UnlinkErrorTest {

        @Test
        void 상태_401은_예외를_전파해_워커가_재시도하도록_한다() {
            server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> client.unlink("kakao-provider-id"))
                .isInstanceOf(Exception.class);

            server.verify();
        }

        @Test
        void 상태_5xx는_예외를_전파해_워커가_재시도하도록_한다() {
            server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> client.unlink("kakao-provider-id"))
                .isInstanceOf(Exception.class);

            server.verify();
        }
    }

    @Nested
    @DisplayName("unlink - adminKey 미설정")
    class UnlinkNoAdminKeyTest {

        @Test
        void adminKey가_비어있으면_요청을_보내지_않는다() {
            RestClient mockRestClient = mock(RestClient.class);
            KakaoOAuth2Client emptyKeyClient = new KakaoOAuth2Client(mockRestClient, "");

            emptyKeyClient.unlink("kakao-provider-id");

            verify(mockRestClient, never()).post();
        }
    }
}
