package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KakaoOAuth2ClientSuccessTest {

    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec uriSpec;
    @Mock RestClient.RequestBodySpec bodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    private void stubRestClientChain() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Nested
    @DisplayName("unlink - 성공")
    class UnlinkTest {

        @Test
        void adminKey가_설정된_상태에서_providerId로_Kakao_연동을_해제한다() {
            stubRestClientChain();
            KakaoOAuth2Client client = new KakaoOAuth2Client(restClient, "valid-admin-key");

            client.unlink("kakao-provider-id");

            verify(restClient).post();
        }

        @Test
        void adminKey가_설정되지_않은_상태에서_unlink를_호출한다() {
            KakaoOAuth2Client client = new KakaoOAuth2Client(restClient, "");

            client.unlink("kakao-provider-id");

            verify(restClient, never()).post();
        }

        @Test
        void unlink_HTTP_호출_중_예외가_발생한다() {
            stubRestClientChain();
            when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("network error"));
            KakaoOAuth2Client client = new KakaoOAuth2Client(restClient, "valid-admin-key");

            assertThatCode(() -> client.unlink("kakao-provider-id"))
                .doesNotThrowAnyException();
        }
    }
}
