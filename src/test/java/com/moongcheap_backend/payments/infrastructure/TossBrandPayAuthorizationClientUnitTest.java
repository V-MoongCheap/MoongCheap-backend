package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossBrandPayAuthorizationClientUnitTest {

    @Test
    void 시크릿키를_Basic_인증하고_AuthorizationCode로_토큰을_발급한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayAuthorizationClient client = new TossBrandPayAuthorizationClient(
            builder.build(), "https://api.tosspayments.com", "test-secret-key");
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
            "test-secret-key:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(
                "https://api.tosspayments.com/v1/brandpay/authorizations/access-token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", authorization))
            .andExpect(header("Idempotency-Key", "issue-idempotency-key"))
            .andExpect(content().json("""
                {
                  "customerKey": "Secure_customerKey.1",
                  "grantType": "AuthorizationCode",
                  "code": "authorization-code"
                }
                """))
            .andRespond(withSuccess("""
                {
                  "accessToken": "access",
                  "refreshToken": "refresh",
                  "tokenType": "bearer",
                  "expiresIn": 3600
                }
                """, MediaType.APPLICATION_JSON));

        BrandPayAuthorizationClient.TokenResponse response =
            client.issue(
                "Secure_customerKey.1", "authorization-code", "issue-idempotency-key");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.expiresIn()).isEqualTo(3600);
        server.verify();
    }

    @Test
    void RefreshToken으로_새로운_인증토큰을_발급한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayAuthorizationClient client = new TossBrandPayAuthorizationClient(
            builder.build(), "https://api.tosspayments.com", "test-secret-key");
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
            "test-secret-key:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(
                "https://api.tosspayments.com/v1/brandpay/authorizations/access-token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", authorization))
            .andExpect(header("Idempotency-Key", "refresh-idempotency-key"))
            .andExpect(content().json("""
                {
                  "customerKey": "Secure_customerKey.1",
                  "grantType": "RefreshToken",
                  "refreshToken": "old-refresh-token"
                }
                """))
            .andRespond(withSuccess("""
                {
                  "accessToken": "new-access",
                  "refreshToken": "new-refresh",
                  "tokenType": "bearer",
                  "expiresIn": 7200
                }
                """, MediaType.APPLICATION_JSON));

        BrandPayAuthorizationClient.TokenResponse response =
            client.refresh(
                "Secure_customerKey.1", "old-refresh-token", "refresh-idempotency-key");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.expiresIn()).isEqualTo(7200);
        server.verify();
    }
}
