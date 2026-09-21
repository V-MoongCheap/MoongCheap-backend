package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionTestHelper {

    private static final String COOKIE_NAME = "SESSION";

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    public Cookie loginAs(Long memberId) {
        return loginAs(new SessionPrincipal(
            memberId,
            "test-login-" + memberId,
            "테스트닉네임" + memberId,
            Set.of(MemberRole.BUYER),
            false,
            true
        ));
    }

    public Cookie loginAs(SessionPrincipal principal) {
        return createSession(sessionRepository, principal);
    }

    private <S extends Session> Cookie createSession(
        SessionRepository<S> repo, SessionPrincipal principal) {
        S session = repo.createSession();
        session.setAttribute(AuthSessionManager.PRINCIPAL_ATTR, principal);
        session.setAttribute(
            AuthSessionManager.MEMBER_INDEX_ATTR,
            String.valueOf(principal.memberId())
        );
        repo.save(session);
        String cookieValue = Base64.getEncoder()
            .encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
        return new Cookie(COOKIE_NAME, cookieValue);
    }
}
