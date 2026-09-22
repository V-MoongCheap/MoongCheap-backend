package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2ClientSuccessTest {

    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec uriSpec;
    @Mock RestClient.RequestBodySpec bodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    @InjectMocks
    GoogleOAuth2Client googleOAuth2Client;

    private void stubRestClientChain() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Nested
    @DisplayName("revoke - 성공")
    class RevokeTest {

        @Test
        void 유효한_token으로_Google_토큰을_revoke한다() {
            stubRestClientChain();

            googleOAuth2Client.revoke("valid-token");

            verify(restClient).post();
        }

        @Test
        void token이_null인_상태에서_revoke를_호출한다() {
            googleOAuth2Client.revoke(null);

            verify(restClient, never()).post();
        }

        @Test
        void token이_빈_문자열인_상태에서_revoke를_호출한다() {
            googleOAuth2Client.revoke("   ");

            verify(restClient, never()).post();
        }

        @Test
        void revoke_HTTP_호출_중_예외가_발생한다() {
            stubRestClientChain();
            when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("network error"));

            assertThatCode(() -> googleOAuth2Client.revoke("valid-token"))
                .doesNotThrowAnyException();
        }
    }
}
