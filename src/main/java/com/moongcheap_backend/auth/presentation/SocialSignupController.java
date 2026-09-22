package com.moongcheap_backend.auth.presentation;

import com.moongcheap_backend.auth.application.SocialSignupCompleteService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.SocialSignupCompleteRequestDto;
import com.moongcheap_backend.common.security.SessionPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입, 로그인, 로그아웃, 비밀번호 변경, 탈퇴")
@RestController
@RequestMapping("/api/auth/social-signup")
@RequiredArgsConstructor
public class SocialSignupController {

    private final SocialSignupCompleteService socialSignupCompleteService;
    private final AuthSessionManager sessionManager;

    @Operation(summary = "소셜 가입 완료", description = "FN-B01-02. 약관 동의 및 선택적 닉네임 설정으로 소셜 가입을 완료한다.")
    @PostMapping("/complete")
    public ResponseEntity<Void> complete(
        SessionPrincipal principal,
        @RequestBody @Valid SocialSignupCompleteRequestDto request,
        HttpServletRequest httpRequest) {
        SessionPrincipal refreshed = socialSignupCompleteService.complete(principal.memberId(), request);
        httpRequest.changeSessionId();
        sessionManager.refreshPrincipal(httpRequest, refreshed);
        return ResponseEntity.noContent().build();
    }
}
