package com.moongcheap_backend.member.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.application.ProfileService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.member.presentation.dto.ProfileEditRequestDto;
import com.moongcheap_backend.member.presentation.dto.ProfileResponseDto;
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
class ProfileServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private NicknameService nicknameService;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private ProfileService service;

    @Nested
    @DisplayName("detail - 성공")
    class DetailTest {

        @Test
        void 일반_회원의_프로필을_조회한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.isSeller()).thenReturn(false);
            when(member.getPhoneNumber()).thenReturn("encrypted");
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            SocialCredential social = mock(SocialCredential.class);
            when(social.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(socialCredentialRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(social));
            when(encryptionService.decrypt("encrypted")).thenReturn("01012345678");
            when(encryptionService.maskPhoneNumber("01012345678")).thenReturn("010-****-5678");

            ProfileResponseDto result = service.detail(memberId);

            assertThat(result.seller()).isNull();
            assertThat(result.phoneNumberMasked()).isEqualTo("010-****-5678");
            assertThat(result.linkedProviders()).containsExactly(SocialProvider.KAKAO);
        }

        @Test
        void 판매자_회원의_프로필을_조회한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.isSeller()).thenReturn(true);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());
            Seller seller = mock(Seller.class);
            when(seller.getBusinessName()).thenReturn("문치프 스토어");
            when(seller.getStatus()).thenReturn(SellerStatus.APPROVED);
            when(sellerRepository.findByMemberIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(seller));

            ProfileResponseDto result = service.detail(memberId);

            assertThat(result.seller()).isNotNull();
            assertThat(result.seller().businessName()).isEqualTo("문치프 스토어");
            assertThat(result.seller().status()).isEqualTo("APPROVED");
        }

        @Test
        void 전화번호가_없는_회원의_프로필을_조회한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.isSeller()).thenReturn(false);
            when(member.getPhoneNumber()).thenReturn(null);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());

            ProfileResponseDto result = service.detail(memberId);

            assertThat(result.phoneNumberMasked()).isNull();
            verify(encryptionService, never()).decrypt(anyString());
        }

        @Test
        void 소셜_연동이_없는_회원의_프로필을_조회한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.isSeller()).thenReturn(false);
            when(member.getPhoneNumber()).thenReturn(null);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());

            ProfileResponseDto result = service.detail(memberId);

            assertThat(result.linkedProviders()).isEmpty();
        }
    }

    @Nested
    @DisplayName("edit - 성공")
    class EditTest {

        @Test
        void 현재_닉네임과_다른_닉네임으로_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.getNickname()).thenReturn("alice");
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ProfileEditRequestDto request = new ProfileEditRequestDto("Bob", null, null, null);

            service.edit(memberId, request);

            verify(nicknameService).ensureAvailable("bob");
            verify(member).changeProfile(eq("bob"), isNull(), isNull(), isNull());
        }

        @Test
        void 현재_닉네임과_대소문자만_다른_닉네임으로_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(member.getNickname()).thenReturn("alice");
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ProfileEditRequestDto request = new ProfileEditRequestDto("Alice", null, null, null);

            service.edit(memberId, request);

            verify(nicknameService, never()).ensureAvailable(anyString());
            verify(member).changeProfile(eq("alice"), isNull(), isNull(), isNull());
        }

        @Test
        void nickname을_변경하지_않고_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ProfileEditRequestDto request = new ProfileEditRequestDto(null, null, null, null);

            service.edit(memberId, request);

            verify(nicknameService, never()).ensureAvailable(anyString());
            verify(member).changeProfile(isNull(), isNull(), isNull(), isNull());
        }

        @Test
        void blank_nickname으로_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ProfileEditRequestDto request = new ProfileEditRequestDto("   ", null, null, null);

            service.edit(memberId, request);

            verify(nicknameService, never()).ensureAvailable(anyString());
            verify(member).changeProfile(isNull(), isNull(), isNull(), isNull());
        }

        @Test
        void 하이픈_포함_전화번호로_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");
            ProfileEditRequestDto request = new ProfileEditRequestDto(
                null, "010-1234-5678", null, null);

            service.edit(memberId, request);

            verify(encryptionService).encrypt("01012345678");
            verify(member).changeProfile(isNull(), isNull(), eq("encrypted"), isNull());
        }

        @Test
        void 전화번호를_변경하지_않고_프로필을_수정한다() {
            Long memberId = 1L;
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            ProfileEditRequestDto request = new ProfileEditRequestDto(null, null, null, null);

            service.edit(memberId, request);

            verify(encryptionService, never()).encrypt(anyString());
            verify(member).changeProfile(isNull(), isNull(), isNull(), isNull());
        }
    }
}
