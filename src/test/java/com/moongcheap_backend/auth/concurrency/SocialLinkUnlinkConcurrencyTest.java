package com.moongcheap_backend.auth.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.application.SocialLinkService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SocialCredentialFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 1-1: SocialLink unlink 마지막 credential 방지")
class SocialLinkUnlinkConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired
    private SocialLinkService socialLinkService;
    @Autowired
    private SocialCredentialRepository socialCredentialRepository;
    @Autowired
    private LocalCredentialRepository localCredentialRepository;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocialCredentialFixture socialCredentialFixture;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("소셜연동유저").getId();
    }

    @Test
    @DisplayName("N개의 소셜 연동만 있을 때 동시에 N개 모두 unlink 시도 시 N-1개만 성공하고 최소 1개의 credential은 남음")
    void onlyNMinusOneUnlinksSucceedWhenNoLocal() throws Exception {
        // 1. 테스트에 사용할 N개의 소셜 프로바이더 준비 (예: 4개)
        List<SocialProvider> providers = List.of(
            SocialProvider.KAKAO,
            SocialProvider.GOOGLE
        );
        int threadCount = providers.size();

        // 2. N개의 소셜 Credential 사전 생성
        for (int i = 0; i < threadCount; i++) {
            SocialProvider provider = providers.get(i);
            socialCredentialFixture.save(memberId, provider, provider.name().toLowerCase() + "-id");
        }

        // 3. 동시성 테스트 실행 (각 스레드가 서로 다른 provider를 unlink 시도)
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                SocialProvider target = providers.get(idx);
                socialLinkService.unlink(memberId, target);
                return true;
            } catch (BusinessException e) {
                // 마지막 로그인 수단 해제 불가 예외 발생 시만 정상 실패(false) 처리
                if (e.getErrorCode() == ErrorCode.LAST_CREDENTIAL_CANNOT_UNLINK) {
                    return false;
                }
                throw e; // 그 외 락 타임아웃/DB 오류 등 의도하지 않은 예외 발생 시 테스트 즉시 실패
            }
        });

        // 4. 결과 검증
        assertThat(result.success()).isEqualTo(threadCount - 1);
        assertThat(result.failure()).isEqualTo(1);

        long remainingSocialCount = socialCredentialRepository.countByMemberId(memberId);
        boolean hasLocal = localCredentialRepository.existsByMemberId(memberId);

        // 로컬 계정이 없으므로 최종 남은 소셜 수단은 정확히 1개여야 함
        assertThat(remainingSocialCount).isEqualTo(1L);
        assertThat(remainingSocialCount + (hasLocal ? 1 : 0)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("1-4: 동일 소셜 credential에 50회 동시 unlink 시 1건만 성공, 나머지는 NOT_FOUND")
    void onlyOneUnlinkSucceedsForSameProvider() throws Exception {
        int threadCount = 50;
        // KAKAO + GOOGLE 모두 생성 → KAKAO unlink 후에도 GOOGLE이 남아 마지막 credential 아님
        socialCredentialFixture.save(memberId, SocialProvider.KAKAO, "kakao-id");
        socialCredentialFixture.save(memberId, SocialProvider.GOOGLE, "google-id");

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                socialLinkService.unlink(memberId, SocialProvider.KAKAO);
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.NOT_FOUND) {
                    return false;
                }
                throw e;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(socialCredentialRepository.countByMemberId(memberId)).isEqualTo(1L);
    }
}