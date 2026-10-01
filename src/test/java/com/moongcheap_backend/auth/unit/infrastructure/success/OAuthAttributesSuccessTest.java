package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.moongcheap_backend.auth.infrastructure.oauth.OAuthAttributes;
import com.moongcheap_backend.member.domain.SocialProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OAuthAttributesSuccessTest {

    @Nested
    @DisplayName("of - 성공")
    class OfTest {

        @Test
        void kakao_registrationId와_유효한_카카오_속성으로_파싱한다() {
            Map<String, Object> profile = Map.of("nickname", "카카오닉네임");
            Map<String, Object> account = new HashMap<>();
            account.put("email", "kakao@example.com");
            account.put("profile", profile);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", 123456L);
            attributes.put("kakao_account", account);

            OAuthAttributes result = OAuthAttributes.of("kakao", attributes);

            assertAll(
                () -> assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO),
                () -> assertThat(result.providerUserId()).isEqualTo("123456"),
                () -> assertThat(result.email()).isEqualTo("kakao@example.com"),
                () -> assertThat(result.nickname()).isEqualTo("카카오닉네임")
            );
        }

        @Test
        void google_registrationId와_유효한_구글_속성으로_파싱한다() {
            Map<String, Object> attributes = Map.of(
                "sub", "google-sub-id",
                "email", "google@example.com",
                "name", "구글이름"
            );

            OAuthAttributes result = OAuthAttributes.of("google", attributes);

            assertAll(
                () -> assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE),
                () -> assertThat(result.providerUserId()).isEqualTo("google-sub-id"),
                () -> assertThat(result.email()).isEqualTo("google@example.com"),
                () -> assertThat(result.nickname()).isEqualTo("구글이름")
            );
        }

        @Test
        void kakao_속성에서_email과_nickname이_null인_경우_파싱한다() {
            Map<String, Object> account = new HashMap<>();
            // email, profile 없음
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", 789L);
            attributes.put("kakao_account", account);

            OAuthAttributes result = OAuthAttributes.of("kakao", attributes);

            assertAll(
                () -> assertThat(result.email()).isNull(),
                () -> assertThat(result.nickname()).isNull()
            );
        }
    }
}
