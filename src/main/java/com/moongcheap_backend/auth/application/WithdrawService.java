package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.application.event.MemberWithdrawnEvent;
import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.PendingProviderUnlinkRepository;
import com.moongcheap_backend.auth.presentation.dto.WithdrawRequestDto;
import com.moongcheap_backend.auth.infrastructure.port.WithdrawEligibilityChecker;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final SocialCredentialRepository socialCredentialRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final NotificationOptOutRepository notificationOptOutRepository;
    private final PendingProviderUnlinkRepository pendingProviderUnlinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final WithdrawEligibilityChecker eligibilityChecker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw(Long memberId, WithdrawRequestDto request) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<LocalCredential> localCredential = localCredentialRepository.findByMemberId(memberId);

        if (localCredential.isPresent()) {
            verifyLocalWithdraw(request, localCredential.get());
        } else {
            verifySocialWithdraw();
        }

        eligibilityChecker.ensureWithdrawable(memberId);

        List<SocialCredential> socials = socialCredentialRepository.findAllByMemberId(memberId);
        LocalDateTime now = LocalDateTime.now();
        for (SocialCredential cred : socials) {
            pendingProviderUnlinkRepository.save(PendingProviderUnlink.of(
                memberId,
                cred.getProvider(),
                cred.getProviderId(),
                cred.getRefreshTokenEnc(),
                now
            ));
        }

        shippingAddressRepository.deleteAllByMemberId(memberId);
        notificationOptOutRepository.deleteAllByMemberId(memberId);
        socialCredentialRepository.deleteByMemberId(memberId);
        localCredentialRepository.deleteByMemberId(memberId);

        member.withdraw();

        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
    }

    private void verifyLocalWithdraw(WithdrawRequestDto request, LocalCredential credential) {
        if (request == null || request.password() == null || request.password().isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
        if (!passwordEncoder.matches(request.password(), credential.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }

    private void verifySocialWithdraw() {
        // TODO: 이메일 인증 추가 예정
    }
}
