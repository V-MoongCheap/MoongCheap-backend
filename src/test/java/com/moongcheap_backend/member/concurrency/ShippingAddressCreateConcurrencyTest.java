package com.moongcheap_backend.member.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.application.ShippingAddressService;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressRequestDto;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ShippingAddressFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 2-2: 배송지 5개 제한")
class ShippingAddressCreateConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private ShippingAddressService shippingAddressService;
    @Autowired private ShippingAddressRepository shippingAddressRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private ShippingAddressFixture shippingAddressFixture;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("배송지 4개인 상태에서 50회 동시 create 시 정확히 1건만 성공, 최종 5개 유지")
    void enforcesLimitUnderConcurrency() throws Exception {
        int threadCount = 50;
        Long memberId = memberFixture.save("배송지제한유저").getId();
        for (int i = 0; i < 4; i++) {
            shippingAddressFixture.save(memberId, "기존" + i, i == 0);
        }

        ShippingAddressRequestDto request = new ShippingAddressRequestDto(
            "새주소", "홍길동", "010-1234-5678", "06235",
            "서울특별시 강남구 테헤란로 427", "101동", "1234#", "문 앞",
            false
        );

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                shippingAddressService.create(memberId, request);
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED) throw e;
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(shippingAddressRepository.countByMemberId(memberId)).isEqualTo(5L);
    }

    @Test
    @DisplayName("2-5: 배송지 0개인 상태에서 50회 동시 create 시 정확히 5건만 성공, 나머지는 제한 초과")
    void exactlyFiveSucceedFromEmptyUnderConcurrency() throws Exception {
        int threadCount = 50;
        Long memberId = memberFixture.save("배송지동시생성유저").getId();

        ShippingAddressRequestDto request = new ShippingAddressRequestDto(
            "새주소", "홍길동", "010-1234-5678", "06235",
            "서울특별시 강남구 테헤란로 427", "101동", "1234#", "문 앞",
            false
        );

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                shippingAddressService.create(memberId, request);
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED) throw e;
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(5);
        assertThat(result.failure()).isEqualTo(threadCount - 5);
        assertThat(shippingAddressRepository.countByMemberId(memberId)).isEqualTo(5L);
    }
}
