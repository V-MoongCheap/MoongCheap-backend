package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.payments.domain.BrandPayToken;
import com.moongcheap_backend.payments.domain.CustomerKey;
import com.moongcheap_backend.payments.infrastructure.BrandPayAuthorizationClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayAuthorizationClient.TokenResponse;
import com.moongcheap_backend.payments.infrastructure.BrandPayTokenRepository;
import com.moongcheap_backend.payments.infrastructure.CustomerKeyRepository;
import com.moongcheap_backend.payments.presentation.dto.BrandPayAuthorizationRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BrandPayTokenServiceUnitTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private CustomerKeyRepository customerKeyRepository;
    @Mock
    private BrandPayTokenRepository brandPayTokenRepository;
    @Mock
    private BrandPayAuthorizationClient authorizationClient;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;

    @InjectMocks
    private BrandPayTokenService service;

    private Member member;
    private CustomerKey customerKey;

    @BeforeEach
    void setUp() {
        member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        customerKey = new CustomerKey(member, "Secure_customerKey.1");
    }

    @Test
    void SDK_인증코드를_토큰으로_교환하고_암호화해_저장한다() {
        TokenResponse response = new TokenResponse("access", "refresh", "bearer", 3600);
        LocalDateTime before = LocalDateTime.now();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(idempotencyKeyGenerator.forIssue(1L, "authorization-code"))
            .thenReturn("issue-idempotency-key");
        when(authorizationClient.issue(
            "Secure_customerKey.1", "authorization-code", "issue-idempotency-key"))
            .thenReturn(response);
        when(encryptionService.encrypt("access")).thenReturn("encrypted-access");
        when(encryptionService.encrypt("refresh")).thenReturn("encrypted-refresh");
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.empty());

        service.issue(1L,
            new BrandPayAuthorizationRequest("Secure_customerKey.1", "authorization-code"));

        ArgumentCaptor<BrandPayToken> captor = ArgumentCaptor.forClass(BrandPayToken.class);
        verify(brandPayTokenRepository).saveAndFlush(captor.capture());
        BrandPayToken saved = captor.getValue();
        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getAccessToken()).isEqualTo("encrypted-access");
        assertThat(saved.getRefreshToken()).isEqualTo("encrypted-refresh");
        assertThat(saved.getAccessTokenExpiresAt())
            .isBetween(before.plusSeconds(3600), LocalDateTime.now().plusSeconds(3600));
    }

    @Test
    void 기존_토큰이_있으면_새로운_토큰으로_갱신한다() {
        BrandPayToken saved = new BrandPayToken(
            member, "old-access", "old-refresh", LocalDateTime.now().minusMinutes(1));
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(idempotencyKeyGenerator.forIssue(1L, "authorization-code"))
            .thenReturn("issue-idempotency-key");
        when(authorizationClient.issue(
            "Secure_customerKey.1", "authorization-code", "issue-idempotency-key"))
            .thenReturn(new TokenResponse("access", "refresh", "bearer", 3600));
        when(encryptionService.encrypt("access")).thenReturn("new-access");
        when(encryptionService.encrypt("refresh")).thenReturn("new-refresh");
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.of(saved));

        service.issue(1L,
            new BrandPayAuthorizationRequest("Secure_customerKey.1", "authorization-code"));

        assertThat(saved.getAccessToken()).isEqualTo("new-access");
        assertThat(saved.getRefreshToken()).isEqualTo("new-refresh");
        verify(brandPayTokenRepository).saveAndFlush(saved);
    }

    @Test
    void 로그인_회원의_customerKey와_다르면_토큰을_요청하지_않는다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));

        assertThatThrownBy(() -> service.issue(1L,
            new BrandPayAuthorizationRequest("Other_customerKey.2", "authorization-code")))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.BRAND_PAY_CUSTOMER_MISMATCH));

        verify(authorizationClient, never()).issue(any(), any(), any());
        verify(brandPayTokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void 저장된_RefreshToken을_복호화해_새로운_토큰으로_갱신한다() {
        BrandPayToken saved = new BrandPayToken(
            member, "encrypted-old-access", "encrypted-old-refresh",
            LocalDateTime.now().minusMinutes(1));
        TokenResponse response = new TokenResponse(
            "new-access", "new-refresh", "bearer", 7200);
        LocalDateTime before = LocalDateTime.now();
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(idempotencyKeyGenerator.forRefresh(1L, "encrypted-old-access"))
            .thenReturn("refresh-idempotency-key");
        when(encryptionService.decrypt("encrypted-old-refresh"))
            .thenReturn("old-refresh");
        when(authorizationClient.refresh(
            "Secure_customerKey.1", "old-refresh", "refresh-idempotency-key"))
            .thenReturn(response);
        when(encryptionService.encrypt("new-access")).thenReturn("encrypted-new-access");
        when(encryptionService.encrypt("new-refresh")).thenReturn("encrypted-new-refresh");

        service.refresh(1L);

        assertThat(saved.getAccessToken()).isEqualTo("encrypted-new-access");
        assertThat(saved.getRefreshToken()).isEqualTo("encrypted-new-refresh");
        assertThat(saved.getAccessTokenExpiresAt())
            .isBetween(before.plusSeconds(7200), LocalDateTime.now().plusSeconds(7200));
        verify(brandPayTokenRepository).saveAndFlush(saved);
    }

    @Test
    void 저장된_브랜드페이_토큰이_없으면_갱신하지_않는다() {
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(1L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.BRAND_PAY_TOKEN_NOT_FOUND));

        verify(authorizationClient, never()).refresh(any(), any(), any());
    }

    @Test
    void AccessToken이_유효하면_복호화한_기존_토큰을_반환한다() {
        BrandPayToken saved = new BrandPayToken(
            member, "encrypted-access", "encrypted-refresh",
            LocalDateTime.now().plusMinutes(10));
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(encryptionService.decrypt("encrypted-access")).thenReturn("valid-access");

        String accessToken = service.getValidAccessToken(1L);

        assertThat(accessToken).isEqualTo("valid-access");
        verify(authorizationClient, never()).refresh(any(), any(), any());
        verify(brandPayTokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void AccessToken이_만료됐으면_갱신하고_새로운_토큰을_반환한다() {
        BrandPayToken saved = new BrandPayToken(
            member, "encrypted-old-access", "encrypted-old-refresh",
            LocalDateTime.now().minusSeconds(1));
        TokenResponse response = new TokenResponse(
            "new-access", "new-refresh", "bearer", 7200);
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(idempotencyKeyGenerator.forRefresh(1L, "encrypted-old-access"))
            .thenReturn("refresh-idempotency-key");
        when(encryptionService.decrypt("encrypted-old-refresh"))
            .thenReturn("old-refresh");
        when(authorizationClient.refresh(
            "Secure_customerKey.1", "old-refresh", "refresh-idempotency-key"))
            .thenReturn(response);
        when(encryptionService.encrypt("new-access")).thenReturn("encrypted-new-access");
        when(encryptionService.encrypt("new-refresh")).thenReturn("encrypted-new-refresh");

        String accessToken = service.getValidAccessToken(1L);

        assertThat(accessToken).isEqualTo("new-access");
        assertThat(saved.getAccessToken()).isEqualTo("encrypted-new-access");
        assertThat(saved.getRefreshToken()).isEqualTo("encrypted-new-refresh");
        verify(brandPayTokenRepository).saveAndFlush(saved);
    }

    @Test
    void AccessToken의_만료가_1분_이내면_미리_갱신한다() {
        BrandPayToken saved = new BrandPayToken(
            member, "encrypted-old-access", "encrypted-old-refresh",
            LocalDateTime.now().plusSeconds(30));
        TokenResponse response = new TokenResponse(
            "new-access", "new-refresh", "bearer", 7200);
        when(brandPayTokenRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(idempotencyKeyGenerator.forRefresh(1L, "encrypted-old-access"))
            .thenReturn("refresh-idempotency-key");
        when(encryptionService.decrypt("encrypted-old-refresh"))
            .thenReturn("old-refresh");
        when(authorizationClient.refresh(
            "Secure_customerKey.1", "old-refresh", "refresh-idempotency-key"))
            .thenReturn(response);
        when(encryptionService.encrypt("new-access")).thenReturn("encrypted-new-access");
        when(encryptionService.encrypt("new-refresh")).thenReturn("encrypted-new-refresh");

        String accessToken = service.getValidAccessToken(1L);

        assertThat(accessToken).isEqualTo("new-access");
        verify(authorizationClient).refresh(
            "Secure_customerKey.1", "old-refresh", "refresh-idempotency-key");
    }
}
