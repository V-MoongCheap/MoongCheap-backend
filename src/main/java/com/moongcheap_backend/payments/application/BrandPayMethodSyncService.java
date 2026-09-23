package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.ProviderCode;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.Account;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.Card;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.MethodsResponse;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토스의 전체 활성 결제수단 응답을 회원의 로컬 결제수단 상태와 동기화한다. */
@Service
@RequiredArgsConstructor
public class BrandPayMethodSyncService {

    private static final String ENABLED = "ENABLED";

    private final MemberRepository memberRepository;
    private final BrandPayMethodRepository brandPayMethodRepository;

    /**
     * methodKey를 기준으로 신규 수단은 생성하고 기존 수단은 갱신한다.
     * 토스의 성공한 전체 조회 결과에서 사라진 기존 수단은 주문 참조 보존을 위해
     * 삭제하지 않고 EXPIRED로 변경한다.
     */
    @Transactional
    public void synchronize(Long memberId, MethodsResponse response) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<BrandPayMethod> existingMethods =
            brandPayMethodRepository.findAllByMemberId(memberId);
        Map<String, BrandPayMethod> existingByMethodKey = new HashMap<>();
        for (BrandPayMethod method : existingMethods) {
            existingByMethodKey.put(method.getMethodKey(), method);
        }

        List<MethodSnapshot> snapshots = toSnapshots(response);
        Set<String> receivedMethodKeys = new HashSet<>();
        List<BrandPayMethod> synchronizedMethods = new ArrayList<>(existingMethods);

        Set<String> snapshotMethodKeys = snapshots.stream()
            .map(MethodSnapshot::methodKey)
            .collect(java.util.stream.Collectors.toSet());
        String localDefaultMethodKey = existingMethods.stream()
            .filter(method -> method.getIsDefault()
                && snapshotMethodKeys.contains(method.getMethodKey()))
            .map(BrandPayMethod::getMethodKey)
            .findFirst()
            .orElse(null);

        // 아래 saveAll에서 새 기본값이 먼저 반영되더라도 회원당 1개 UNIQUE 제약과
        // 충돌하지 않도록 DB의 기존 기본 표시를 먼저 해제한다. clearAutomatically로
        // 분리된 엔티티들은 마지막 saveAll에서 최종 상태로 병합된다.
        brandPayMethodRepository.unmarkAllDefaults(memberId);

        for (MethodSnapshot snapshot : snapshots) {
            if (!receivedMethodKeys.add(snapshot.methodKey())) {
                throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_SYNC_FAILED);
            }

            // 사용자가 앱에서 고른 기본값을 우선 보존한다. 유효한 로컬 기본값이 없을 때만
            // 토스가 내려준 최근 등록·사용 수단을 최초 기본값으로 사용한다.
            boolean isDefault = localDefaultMethodKey != null
                ? Objects.equals(localDefaultMethodKey, snapshot.methodKey())
                : Objects.equals(response.selectedMethodId(), snapshot.externalId());
            BrandPayMethod existing = existingByMethodKey.get(snapshot.methodKey());
            if (existing == null) {
                synchronizedMethods.add(new BrandPayMethod(
                    member,
                    snapshot.methodKey(),
                    snapshot.providerCode(),
                    snapshot.maskedNumber(),
                    snapshot.type(),
                    isDefault
                ));
            } else {
                existing.synchronize(
                    snapshot.providerCode(),
                    snapshot.maskedNumber(),
                    snapshot.type(),
                    isDefault
                );
            }
        }

        for (BrandPayMethod existing : existingMethods) {
            if (!receivedMethodKeys.contains(existing.getMethodKey())) {
                existing.expire();
            }
        }

        brandPayMethodRepository.saveAllAndFlush(synchronizedMethods);
    }

    /** 카드와 계좌 배열을 로컬 저장에 필요한 공통 형태로 평탄화한다. */
    private List<MethodSnapshot> toSnapshots(MethodsResponse response) {
        List<MethodSnapshot> snapshots = new ArrayList<>();

        for (Card card : response.cards()) {
            if (ENABLED.equalsIgnoreCase(card.status())) {
                snapshots.add(new MethodSnapshot(
                    requireValue(card.id()),
                    requireValue(card.methodKey()),
                    cardProvider(requireValue(card.issuerCode())),
                    requireValue(card.cardNumber()),
                    PaymentType.CARD
                ));
            }
        }

        for (Account account : response.accounts()) {
            if (ENABLED.equalsIgnoreCase(account.status())) {
                snapshots.add(new MethodSnapshot(
                    requireValue(account.id()),
                    requireValue(account.methodKey()),
                    financialProvider(requireValue(account.bankCode())),
                    requireValue(account.accountNumber()),
                    PaymentType.ACCOUNT
                ));
            }
        }

        return snapshots;
    }

    /** 알 수 없는 카드사 코드를 내부 구현 예외 대신 결제수단 동기화 오류로 변환한다. */
    private ProviderCode cardProvider(String code) {
        try {
            return ProviderCode.fromCode(ProviderCode.Type.CARD, code);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_SYNC_FAILED);
        }
    }

    /** 계좌 응답의 기관 코드는 은행 또는 증권사일 수 있으므로 두 유형을 순서대로 확인한다. */
    private ProviderCode financialProvider(String code) {
        try {
            return ProviderCode.fromCode(ProviderCode.Type.BANK, code);
        } catch (IllegalArgumentException ignored) {
            try {
                return ProviderCode.fromCode(ProviderCode.Type.SECURITIES, code);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_SYNC_FAILED);
            }
        }
    }

    private String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_SYNC_FAILED);
        }
        return value;
    }

    private record MethodSnapshot(
        String externalId,
        String methodKey,
        ProviderCode providerCode,
        String maskedNumber,
        PaymentType type
    ) {
    }
}
