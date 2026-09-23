package com.moongcheap_backend.auth.infrastructure.oauth;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.application.SocialLinkService;
import com.moongcheap_backend.auth.domain.NicknameValidator;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String ATTR_MEMBER_ID = "memberId";
    public static final String ATTR_PRINCIPAL = "principal";
    public static final String ATTR_TERMS_AGREED = "termsAgreed";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final MemberRepository memberRepository;
    private final SocialCredentialRepository socialCredentialRepository;
    private final NicknameService nicknameService;
    private final PrincipalFactory principalFactory;
    private final SocialLinkService socialLinkService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oauth2User.getAttributes());

        Member member = resolveOrLinkMember(attrs);

        SessionPrincipal principal = principalFactory.build(member);

        Map<String, Object> enrichedAttrs = new HashMap<>(attrs.raw());
        enrichedAttrs.put(ATTR_MEMBER_ID, member.getId());
        enrichedAttrs.put(ATTR_PRINCIPAL, principal);
        enrichedAttrs.put(ATTR_TERMS_AGREED, member.isTermsAgreed());

        String nameAttrKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (nameAttrKey == null || nameAttrKey.isBlank() || !enrichedAttrs.containsKey(nameAttrKey)) {
            nameAttrKey = ATTR_MEMBER_ID;
        }
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_BUYER")),
                enrichedAttrs,
                nameAttrKey
        );
    }

    private Member resolveOrLinkMember(OAuthAttributes attrs) {
        // 이미 해당 소셜 계정과 연결된 회원이 있으면 반환
        var existing = socialCredentialRepository
                .findByProviderAndProviderId(attrs.provider(), attrs.providerUserId());
        if (existing.isPresent()) {
            return memberRepository.findByIdAndDeletedAtIsNull(existing.get().getMemberId())
                    .orElseThrow(() -> new IllegalStateException("Member not found for social credential"));
        }

        // 로그인된 상태면 기존 회원에 소셜 계정 연동
        SessionPrincipal loggedIn = getLoggedInPrincipal();
        if (loggedIn != null) {
            socialLinkService.link(loggedIn.memberId(), attrs.provider(), attrs.providerUserId());
            return memberRepository.findByIdAndDeletedAtIsNull(loggedIn.memberId())
                    .orElseThrow(() -> new IllegalStateException("Member not found: " + loggedIn.memberId()));
        }

        // 신규 회원 생성
        return createNewMember(attrs);
    }

    private SessionPrincipal getLoggedInPrincipal() {
        var requestAttrs = RequestContextHolder.getRequestAttributes();
        if (!(requestAttrs instanceof ServletRequestAttributes servletAttrs)) return null;
        HttpSession session = servletAttrs.getRequest().getSession(false);
        if (session == null) return null;
        return (SessionPrincipal) session.getAttribute(AuthSessionManager.PRINCIPAL_ATTR);
    }

    private Member createNewMember(OAuthAttributes attrs) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                String nickname = nicknameService.allocateForSocial(attrs.nickname());
                Member saved = memberRepository.saveAndFlush(Member.builder()
                        .nickname(NicknameValidator.toKey(nickname))
                        .email(attrs.email())
                        .build());
                socialCredentialRepository.save(SocialCredential.builder()
                        .memberId(saved.getId())
                        .provider(attrs.provider())
                        .providerId(attrs.providerUserId())
                        .build());
                return saved;
            } catch (DataIntegrityViolationException ignored) {
                // 닉네임 충돌 시 재시도
            }
        }
        throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED, "닉네임 자동 발급에 실패했습니다.");
    }
}
