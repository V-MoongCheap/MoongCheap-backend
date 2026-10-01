package com.moongcheap_backend.member.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.application.OrderMemberInfoService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.OrderMemberInfoDto;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderMemberInfoServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private OrderMemberInfoService service;

    @Nested
    @DisplayName("getForOrder - 성공")
    class GetForOrderTest {

        @Test
        void 회원과_배송지_정보를_주문용으로_조회한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            Member member = mock(Member.class);
            when(member.getPhoneNumber()).thenReturn("member-encrypted");
            when(member.getNickname()).thenReturn("홍길동");
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(address.getPhoneNumber()).thenReturn("addr-encrypted");
            when(address.getRecipientName()).thenReturn("홍길동");
            when(address.getZipcode()).thenReturn("06235");
            when(address.getAddress()).thenReturn("서울 강남구");
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(encryptionService.decrypt("member-encrypted")).thenReturn("01011112222");
            when(encryptionService.decrypt("addr-encrypted")).thenReturn("01033334444");

            OrderMemberInfoDto result = service.getForOrder(memberId, addressId);

            assertThat(result.buyerPhoneNumber()).isEqualTo("01011112222");
            assertThat(result.shipping().phoneNumber()).isEqualTo("01033334444");
            assertThat(result.nickname()).isEqualTo("홍길동");
        }
    }

    @Nested
    @DisplayName("validateActiveMember - 성공")
    class ValidateActiveMemberTest {

        @Test
        void 활성_회원_여부를_검증한다() {
            when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

            assertThatCode(() -> service.validateActiveMember(1L))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("getActiveMemberIds - 성공")
    class GetActiveMemberIdsTest {

        @Test
        void 빈_컬렉션으로_활성_회원_ID_목록을_조회한다() {
            Set<Long> result = service.getActiveMemberIds(List.of());

            assertThat(result).isEmpty();
            verifyNoInteractions(memberRepository);
        }

        @Test
        void 유효한_memberIds로_활성_회원_ID_목록을_조회한다() {
            List<Long> memberIds = List.of(1L, 2L, 3L);
            when(memberRepository.findActiveMemberIds(memberIds)).thenReturn(Set.of(1L, 2L));

            Set<Long> result = service.getActiveMemberIds(memberIds);

            assertThat(result).containsExactlyInAnyOrder(1L, 2L);
            verify(memberRepository).findActiveMemberIds(memberIds);
        }
    }
}
