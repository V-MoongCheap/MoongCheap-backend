package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.domain.BusinessNumberValidator;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.SellerRegisterRequestDto;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerRegistrationService {

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final EncryptionService encryptionService;
    private final AuthSessionManager sessionManager;
    private final PrincipalFactory principalFactory;

    // 사업자등록번호의 경우 국세청이 정의한 사업자 등록번호 체크섬 알고리즘을 만을 사용하며 외의 별도의 검사는 하지 않습니다.
    @Transactional
    public Long register(Long memberId, SellerRegisterRequestDto request,
        HttpServletRequest httpRequest) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (sellerRepository.existsByMemberIdAndDeletedAtIsNull(memberId)) {
            throw new BusinessException(ErrorCode.SELLER_ALREADY_REGISTERED);
        }

        String bizNumberDigits = BusinessNumberValidator.normalizeAndValidate(
            request.businessNumber());
        String bizHash = sha256Hex(bizNumberDigits);
        if (sellerRepository.existsByBusinessNumberHashAndDeletedAtIsNull(bizHash)) {
            throw new BusinessException(ErrorCode.BUSINESS_NUMBER_DUPLICATED);
        }
        String mailOrder = request.mailOrderRegistrationNumber().replaceAll("\\s+", "");

        Seller seller = Seller.builder()
            .memberId(memberId)
            .businessName(request.businessName().trim())
            .businessNumber(encryptionService.encrypt(bizNumberDigits))
            .businessNumberHash(bizHash)
            .mailOrderRegistrationNumber(mailOrder)
            .ownerName(request.ownerName().trim())
            .phoneNumber(request.phoneNumber().replaceAll("[^0-9]", ""))
            .build();
        seller.approve();
        Seller saved = sellerRepository.save(seller);

        member.becomeSeller();

        SessionPrincipal refreshed = principalFactory.build(member);
        sessionManager.refreshPrincipal(httpRequest, refreshed);
        return saved.getId();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
