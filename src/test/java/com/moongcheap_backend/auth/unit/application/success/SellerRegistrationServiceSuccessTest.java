package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.application.SellerRegistrationService;
import com.moongcheap_backend.auth.presentation.dto.SellerRegisterRequestDto;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerRegistrationServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private PrincipalFactory principalFactory;

    @InjectMocks
    private SellerRegistrationService sellerRegistrationService;

    @Nested
    @DisplayName("register - 성공")
    class RegisterTest {

        @Test
        void 유효한_정보로_판매자_등록을_요청한다() {
            Long memberId = 1L;
            SellerRegisterRequestDto dto = new SellerRegisterRequestDto(
                "문치프 스토어", "120-81-47521", "2024-서울강남-1234", "홍길동", "010-1234-5678"
            );

            Member member = mock(Member.class);
            Seller seller = mock(Seller.class);
            when(seller.getId()).thenReturn(10L);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(sellerRepository.existsByMemberIdAndDeletedAtIsNull(memberId)).thenReturn(false);
            when(sellerRepository.existsByBusinessNumberHashAndDeletedAtIsNull(any())).thenReturn(false);
            when(encryptionService.encrypt(any())).thenReturn("encrypted");
            when(sellerRepository.save(any(Seller.class))).thenReturn(seller);

            Long result = sellerRegistrationService.register(memberId, dto);

            verify(sellerRepository).save(any(Seller.class));
            verify(member).becomeSeller();
            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("buildRefreshedPrincipal - 성공")
    class BuildRefreshedPrincipalTest {

        @Test
        void 회원_정보로_갱신된_세션_주체를_생성한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            SessionPrincipal expected = mock(SessionPrincipal.class);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(principalFactory.build(member)).thenReturn(expected);

            SessionPrincipal result = sellerRegistrationService.buildRefreshedPrincipal(memberId);

            assertThat(result).isSameAs(expected);
        }
    }
}
