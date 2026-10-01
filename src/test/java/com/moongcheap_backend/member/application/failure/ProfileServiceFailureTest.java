package com.moongcheap_backend.member.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.application.ProfileService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.member.presentation.dto.ProfileEditRequestDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private NicknameService nicknameService;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private ProfileService service;

    @Nested
    @DisplayName("detail - 실패")
    class DetailTest {

        @Test
        void 존재하지_않는_회원의_프로필을_조회한다() {
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 데이터_정합성이_깨진_판매자_회원의_프로필을_조회한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.isSeller()).thenReturn(true);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());
            when(sellerRepository.findByMemberIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detail(memberId))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("edit - 실패")
    class EditTest {

        @Test
        void 존재하지_않는_회원의_프로필을_수정한다() {
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
            ProfileEditRequestDto request = new ProfileEditRequestDto(null, null, null, null);

            assertThatThrownBy(() -> service.edit(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
