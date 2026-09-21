package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.application.SellerRegistrationService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.SellerRegisterRequestDto;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerRegistrationServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private AuthSessionManager sessionManager;
    @Mock private PrincipalFactory principalFactory;

    @InjectMocks
    private SellerRegistrationService sellerRegistrationService;

    // 유효한 사업자등록번호 (국세청 체크섬 통과)
    private static final String VALID_BIZ_NUMBER = "120-81-47521";

    @Nested
    @DisplayName("register - 실패")
    class RegisterFailureTest {

        @Test
        void 존재하지_않는_회원이_판매자_등록을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            SellerRegisterRequestDto dto = new SellerRegisterRequestDto(
                "문치프 스토어", VALID_BIZ_NUMBER, "2024-서울강남-1234", "홍길동", "010-1234-5678"
            );
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sellerRegistrationService.register(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 이미_판매자인_회원이_판매자_등록을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            SellerRegisterRequestDto dto = new SellerRegisterRequestDto(
                "문치프 스토어", VALID_BIZ_NUMBER, "2024-서울강남-1234", "홍길동", "010-1234-5678"
            );
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(sellerRepository.existsByMemberIdAndDeletedAtIsNull(1L)).thenReturn(true);

            assertThatThrownBy(() -> sellerRegistrationService.register(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELLER_ALREADY_REGISTERED);
        }

        @Test
        void 존재하는_사업자_번호로_판매자를_등록한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            SellerRegisterRequestDto dto = new SellerRegisterRequestDto(
                "문치프 스토어", VALID_BIZ_NUMBER, "2024-서울강남-1234", "홍길동", "010-1234-5678"
            );
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(sellerRepository.existsByMemberIdAndDeletedAtIsNull(1L)).thenReturn(false);
            when(sellerRepository.existsByBusinessNumberHashAndDeletedAtIsNull(any())).thenReturn(true);

            assertThatThrownBy(() -> sellerRegistrationService.register(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUSINESS_NUMBER_DUPLICATED);
        }
    }
}
