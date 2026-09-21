package com.moongcheap_backend.member.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.application.OrderMemberInfoService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderMemberInfoServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private OrderMemberInfoService service;

    @Nested
    @DisplayName("getForOrder - 실패")
    class GetForOrderTest {

        @Test
        void 존재하지_않는_회원으로_주문_정보를_조회한다() {
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getForOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 존재하지_않는_배송지로_주문_정보를_조회한다() {
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(shippingAddressRepository.findById(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getForOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }

        @Test
        void 다른_회원_소유의_배송지로_주문_정보를_조회한다() {
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(99L);
            when(shippingAddressRepository.findById(100L)).thenReturn(Optional.of(address));

            assertThatThrownBy(() -> service.getForOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("validateActiveMember - 실패")
    class ValidateActiveMemberTest {

        @Test
        void 탈퇴하거나_존재하지_않는_회원의_활성_여부를_검증한다() {
            when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

            assertThatThrownBy(() -> service.validateActiveMember(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
