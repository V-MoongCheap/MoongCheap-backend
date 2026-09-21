package com.moongcheap_backend.payments.application;

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
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandPayTokenService {

    /*
     * 토큰을 실제 외부 요청에 사용하는 동안 만료되는 일을 피하기 위한 안전 여유다.
     * 만료까지 1분 이하로 남았다면 아직 만료 전이어도 미리 갱신한다.
     */
    private static final Duration ACCESS_TOKEN_REFRESH_BUFFER = Duration.ofMinutes(1);

    private final MemberRepository memberRepository;
    private final CustomerKeyRepository customerKeyRepository;
    private final BrandPayTokenRepository brandPayTokenRepository;
    private final BrandPayAuthorizationClient authorizationClient;
    private final EncryptionService encryptionService;
    private final BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;

    /**
     * 브랜드페이 SDK 인증 결과를 토스페이먼츠 토큰으로 교환해 저장한다.
     *
     * @param memberId 인증된 서비스 회원 ID
     * @param request SDK 인증 완료 후 프론트엔드가 전달한 customerKey와 code
     */
    public void issue(Long memberId, BrandPayAuthorizationRequest request) {
        // 요청한 회원과 해당 회원에게 발급된 CustomerKey가 실제로 존재하는지 확인한다.
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        CustomerKey customerKey = customerKeyRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_KEY_NOT_FOUND));

        // 다른 회원의 인증 결과가 저장되지 않도록 SDK 인증에 사용한 CustomerKey를 검증한다.
        if (!customerKey.getCustomerKey().equals(request.customerKey())) {
            throw new BusinessException(ErrorCode.BRAND_PAY_CUSTOMER_MISMATCH);
        }

        // 동일한 Authorization Code 재시도에는 같은 멱등키를 사용해 토스의 중복 처리를 막는다.
        String idempotencyKey = idempotencyKeyGenerator.forIssue(memberId, request.code());

        // 일회성 인증 코드를 Access Token과 Refresh Token으로 교환한다.
        TokenResponse token = authorizationClient.issue(
            customerKey.getCustomerKey(), request.code(), idempotencyKey);
        save(member, token);
    }

    /**
     * 백엔드에서 토스페이먼츠 API를 호출할 때 사용할 유효한 Access Token을 반환한다.
     *
     * <p>저장된 토큰의 만료까지 1분보다 많이 남았다면 기존 Access Token만 복호화해
     * 반환한다. 이미 만료됐거나 1분 이내에 만료될 예정이면 Refresh Token으로 토큰을
     * 재발급하고, 새 토큰을 DB에 저장한 다음 새 Access Token 원문을 반환한다.</p>
     *
     * <p>반환된 값은 백엔드 내부의 토스 API 호출에만 사용해야 하며 컨트롤러 응답으로
     * 프론트엔드에 노출하면 안 된다.</p>
     *
     * @param memberId Access Token이 필요한 서비스 회원 ID
     * @return 현재 유효하거나 새로 발급받은 Access Token 원문
     */
    public String getValidAccessToken(Long memberId) {
        BrandPayToken savedToken = findToken(memberId);

        if (shouldRefresh(savedToken)) {
            return refreshAndGetAccessToken(memberId, savedToken);
        }

        // 유효기간이 충분히 남은 경우에는 토스 API를 호출하지 않고 저장 토큰을 사용한다.
        String accessToken = encryptionService.decrypt(savedToken.getAccessToken());
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_ACCESS_TOKEN_UNAVAILABLE);
        }
        return accessToken;
    }

    /**
     * 저장된 Refresh Token으로 회원의 브랜드페이 인증 토큰을 갱신한다.
     * 자동결제나 결제수단 조회처럼 유효한 Access Token이 필요한 백엔드 기능에서 호출한다.
     *
     * <p>Refresh Token 원문은 DB에 저장하지 않으므로 외부 API를 호출하기 직전에만
     * 복호화한다. 갱신 응답으로 받은 두 토큰도 즉시 암호화하여 기존 값과 만료 시각을
     * 교체하며, 토큰 원문을 프론트엔드나 메서드 반환값으로 노출하지 않는다.</p>
     *
     * @param memberId 토큰을 갱신할 인증된 서비스 회원 ID
     */
    public void refresh(Long memberId) {
        BrandPayToken savedToken = findToken(memberId);
        refreshAndGetAccessToken(memberId, savedToken);
    }

    /** 저장된 토큰을 조회하고 최초 인증을 완료하지 않은 회원은 명확한 예외로 처리한다. */
    private BrandPayToken findToken(Long memberId) {
        return brandPayTokenRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_PAY_TOKEN_NOT_FOUND));
    }

    /**
     * 만료 시각이 없거나 현재부터 안전 여유 1분 이내라면 갱신이 필요하다고 판단한다.
     * 경계 시각과 정확히 같을 때도 갱신해 만료된 토큰이 외부 요청에 사용되지 않게 한다.
     */
    private boolean shouldRefresh(BrandPayToken savedToken) {
        LocalDateTime expiresAt = savedToken.getAccessTokenExpiresAt();
        LocalDateTime refreshThreshold = LocalDateTime.now()
            .plus(ACCESS_TOKEN_REFRESH_BUFFER);
        return expiresAt == null || !expiresAt.isAfter(refreshThreshold);
    }

    /**
     * Refresh Token으로 두 인증 토큰을 재발급하고 저장이 끝난 뒤 새 Access Token을 반환한다.
     * 저장에 실패하면 예외가 발생하므로 DB에 반영되지 않은 토큰을 호출자에게 반환하지 않는다.
     */
    private String refreshAndGetAccessToken(Long memberId, BrandPayToken savedToken) {
        // 토큰 갱신 요청에는 토큰과 연결된 동일한 CustomerKey가 반드시 필요하다.
        CustomerKey customerKey = customerKeyRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_KEY_NOT_FOUND));

        /*
         * 암호화된 Access Token은 같은 갱신 시도 동안 DB에 안정적으로 남아 있다.
         * 이를 해싱한 멱등키를 사용하면 토스 성공 후 DB 저장에 실패해도 같은 응답을
         * 다시 받을 수 있고, 토큰 원문을 복호화하거나 헤더에 노출할 필요도 없다.
         */
        String encryptedAccessToken = savedToken.getAccessToken();
        if (encryptedAccessToken == null || encryptedAccessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_TOKEN_REFRESH_FAILED);
        }
        String idempotencyKey = idempotencyKeyGenerator.forRefresh(
            memberId, encryptedAccessToken);

        // Refresh Token은 외부 요청에 필요한 짧은 구간에서만 평문으로 복호화한다.
        String refreshToken = encryptionService.decrypt(savedToken.getRefreshToken());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_TOKEN_REFRESH_FAILED);
        }

        // 같은 토큰 발급 API를 RefreshToken grantType으로 호출해 두 토큰을 모두 재발급한다.
        TokenResponse refreshedToken = authorizationClient.refresh(
            customerKey.getCustomerKey(), refreshToken, idempotencyKey);

        update(savedToken, refreshedToken);
        return refreshedToken.accessToken();
    }

    private void save(Member member, TokenResponse token) {
        // 재인증한 회원은 기존 토큰을 갱신하고, 최초 인증한 회원은 새 토큰을 생성한다.
        BrandPayToken brandPayToken = brandPayTokenRepository.findById(member.getId())
            .map(saved -> {
                applyToken(saved, token);
                return saved;
            })
            .orElseGet(() -> new BrandPayToken(
                member,
                encryptionService.encrypt(token.accessToken()),
                encryptionService.encrypt(token.refreshToken()),
                calculateExpiresAt(token)
            ));

        // flush 시점에 @Version 충돌이나 DB 제약조건 오류를 즉시 확인한다.
        brandPayTokenRepository.saveAndFlush(brandPayToken);
    }

    /** 갱신된 토큰을 기존 엔티티에 반영하고 명시적으로 저장한다. */
    private void update(BrandPayToken savedToken, TokenResponse token) {
        applyToken(savedToken, token);
        brandPayTokenRepository.saveAndFlush(savedToken);
    }

    /**
     * 외부에 유출되면 고객 정보 접근 권한으로 사용될 수 있으므로 토큰 원문을 암호화한 뒤
     * 엔티티에 반영한다. Refresh Token도 갱신 응답의 새로운 값으로 반드시 교체한다.
     */
    private void applyToken(BrandPayToken savedToken, TokenResponse token) {
        savedToken.update(
            encryptionService.encrypt(token.accessToken()),
            encryptionService.encrypt(token.refreshToken()),
            calculateExpiresAt(token)
        );
    }

    /** 토스페이먼츠가 초 단위로 반환한 유효기간을 서버의 절대 만료 시각으로 변환한다. */
    private LocalDateTime calculateExpiresAt(TokenResponse token) {
        return LocalDateTime.now().plusSeconds(token.expiresIn());
    }
}
