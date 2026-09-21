package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossBrandPayMethodClientUnitTest {

    @Test
    void AccessToken으로_등록된_카드와_계좌를_모두_조회한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossBrandPayMethodClient client = new TossBrandPayMethodClient(
            builder.build(), "https://api.tosspayments.com");

        server.expect(requestTo(
                "https://api.tosspayments.com/v1/brandpay/payments/methods"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer valid-access-token"))
            .andRespond(withSuccess("""
                {
                  "isIdentified": true,
                  "selectedMethodId": "card-id",
                  "cards": [{
                    "id": "card-id",
                    "methodKey": "card-method-key",
                    "cardNumber": "1234-****-****-5678",
                    "issuerCode": "11",
                    "status": "ENABLED",
                    "cardName": "KB국민카드"
                  }],
                  "accounts": [{
                    "id": "account-id",
                    "methodKey": "account-method-key",
                    "accountNumber": "110-***-123456",
                    "bankCode": "88",
                    "status": "ENABLED",
                    "accountName": "신한 주거래"
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        BrandPayMethodClient.MethodsResponse response =
            client.getAll("valid-access-token");

        assertThat(response.selectedMethodId()).isEqualTo("card-id");
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().getFirst().methodKey()).isEqualTo("card-method-key");
        assertThat(response.accounts()).hasSize(1);
        assertThat(response.accounts().getFirst().methodKey())
            .isEqualTo("account-method-key");
        server.verify();
    }
}
