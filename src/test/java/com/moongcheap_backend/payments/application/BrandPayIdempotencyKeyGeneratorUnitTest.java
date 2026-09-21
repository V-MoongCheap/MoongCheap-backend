package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandPayIdempotencyKeyGeneratorUnitTest {

    private final BrandPayIdempotencyKeyGenerator generator =
        new BrandPayIdempotencyKeyGenerator();

    @Test
    void 같은_회원과_암호화_AccessToken은_같은_갱신_멱등키를_생성한다() {
        String first = generator.forRefresh(1L, "encrypted-access-token");
        String second = generator.forRefresh(1L, "encrypted-access-token");

        assertThat(first)
            .isEqualTo(second)
            .matches("[0-9a-f]{64}");
    }

    @Test
    void 회원이나_암호화_AccessToken이_달라지면_갱신_멱등키도_달라진다() {
        String original = generator.forRefresh(1L, "encrypted-access-token");

        assertThat(generator.forRefresh(2L, "encrypted-access-token"))
            .isNotEqualTo(original);
        assertThat(generator.forRefresh(1L, "another-encrypted-access-token"))
            .isNotEqualTo(original);
    }

    @Test
    void 최초발급과_갱신은_같은_입력이어도_서로_다른_멱등키를_생성한다() {
        String issueKey = generator.forIssue(1L, "same-input");
        String refreshKey = generator.forRefresh(1L, "same-input");

        assertThat(issueKey).isNotEqualTo(refreshKey);
    }
}
