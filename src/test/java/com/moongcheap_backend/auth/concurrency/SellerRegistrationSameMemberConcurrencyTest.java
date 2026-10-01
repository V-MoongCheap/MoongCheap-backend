package com.moongcheap_backend.auth.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.application.SellerRegistrationService;
import com.moongcheap_backend.auth.presentation.dto.SellerRegisterRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("동시성 1-3: 동일 회원이 서로 다른 사업자번호로 이중 등록 시도")
class SellerRegistrationSameMemberConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired
    private SellerRegistrationService sellerRegistrationService;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberFixture memberFixture;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("이중등록유저").getId();
    }

    @Test
    @DisplayName("같은 회원이 서로 다른 사업자번호로 N건 동시 register 시 정확히 1건만 성공하고 나머지는 실패")
    void onlyOneRegistrationSucceedsForSameMember() throws Exception {
        int threadCount = 50;

        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        List<SellerRegisterRequestDto> requests = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String first9 = String.format("12345%04d", i);
            int sum = 0;
            for (int p = 0; p < 9; p++) {
                sum += Character.getNumericValue(first9.charAt(p)) * weights[p];
            }
            sum += (Character.getNumericValue(first9.charAt(8)) * 5) / 10;
            int check = (10 - (sum % 10)) % 10;
            String businessNumber = first9 + check;
            requests.add(new SellerRegisterRequestDto(
                "가게_" + i,
                businessNumber,
                "2024-서울강남-" + (1234 + i),
                "홍길동",
                String.format("010-1111-%04d", i)
            ));
        }

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                sellerRegistrationService.register(memberId, requests.get(idx));
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.SELLER_ALREADY_REGISTERED) {
                    return false;
                }
                throw e;
            } catch (DataIntegrityViolationException e) {
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(sellerRepository.count()).isEqualTo(1L);
        assertThat(memberRepository.findById(memberId).orElseThrow().isSeller()).isTrue();
    }
}
