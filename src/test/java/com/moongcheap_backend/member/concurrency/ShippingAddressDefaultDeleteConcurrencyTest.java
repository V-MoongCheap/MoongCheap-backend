package com.moongcheap_backend.member.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.member.application.ShippingAddressService;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ShippingAddressFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 2-3/2-4: 배송지 기본 지정/삭제")
class ShippingAddressDefaultDeleteConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private ShippingAddressService shippingAddressService;
    @Autowired private ShippingAddressRepository shippingAddressRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private ShippingAddressFixture shippingAddressFixture;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("주소유저").getId();
    }

    @Test
    @DisplayName("2-3: 두 배송지를 동시에 기본 지정 시 최종 기본은 정확히 1개")
    void onlyOneDefaultAfterConcurrentMarkAsDefault() throws Exception {
        ShippingAddress a = shippingAddressFixture.save(memberId, "집", true);
        ShippingAddress b = shippingAddressFixture.save(memberId, "회사", false);
        ShippingAddress c = shippingAddressFixture.save(memberId, "부모님", false);

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(2, idx -> {
            Long target = idx == 0 ? b.getId() : c.getId();
            shippingAddressService.markAsDefault(memberId, target);
            return true;
        });

        long defaultCount = shippingAddressRepository
            .findAllByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId).stream()
            .filter(ShippingAddress::isDefault)
            .count();
        assertThat(defaultCount).isEqualTo(1L);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("2-4: 동일 기본 배송지 50회 동시 삭제 시 1건 성공, 나머지는 NOT_FOUND")
    void onlyOneDeleteSucceedsForSameAddress() throws Exception {
        int threadCount = 50;
        ShippingAddress a = shippingAddressFixture.save(memberId, "집", true);
        shippingAddressFixture.save(memberId, "회사", false);
        shippingAddressFixture.save(memberId, "부모님", false);

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                shippingAddressService.delete(memberId, a.getId());
                return true;
            } catch (BusinessException e) {
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(shippingAddressRepository.findById(a.getId())).isEmpty();

        long defaultCount = shippingAddressRepository
            .findAllByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId).stream()
            .filter(ShippingAddress::isDefault)
            .count();
        assertThat(defaultCount).isEqualTo(1L);
    }
}
