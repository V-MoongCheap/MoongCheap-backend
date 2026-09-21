package com.moongcheap_backend.auth.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.application.SellerRegistrationService;
import com.moongcheap_backend.auth.presentation.dto.SellerRegisterRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
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
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("동시성 1-2: 동일 사업자번호로 서로 다른 회원이 동시 등록")
class SellerRegistrationBusinessNumberConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired
    private SellerRegistrationService sellerRegistrationService;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private MemberFixture memberFixture;


    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("N명의 서로 다른 회원이 같은 유효 사업자번호로 동시 register 시 1건만 성공, 나머지는 BUSINESS_NUMBER_DUPLICATED")
    void onlyOneMemberCanRegisterSameBusinessNumber() throws Exception {
        int threadCount = 50;

        // 1. N명의 서로 다른 회원 사전 생성 및 List 관리
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            memberIds.add(memberFixture.save("판매자_" + i).getId());
        }

        SellerRegisterRequestDto request = new SellerRegisterRequestDto(
            "문치프상회", "123-45-67815", "2024-서울강남-1234",
            "홍길동", "010-1234-5678"
        );

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                // 각 스레드마다 고유한 회원 ID 할당
                Long targetMember = memberIds.get(idx);
                sellerRegistrationService.register(targetMember, request,
                    new MockHttpServletRequest());
                return true;
            } catch (BusinessException e) {
                // 오직 사업자번호 중복 예외만 정상 실패(false)로 간주
                if (e.getErrorCode() == ErrorCode.BUSINESS_NUMBER_DUPLICATED) {
                    return false;
                }
                throw e; // 그 외의 예외(락 타임아웃, 중복 회원 등) 발생 시 즉시 테스트 실패
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(sellerRepository.count()).isEqualTo(1L);
    }
}
