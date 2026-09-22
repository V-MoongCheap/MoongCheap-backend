package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Sort PAYMENT_METHOD_SORT = Sort.by(
        Sort.Order.desc("isDefault"),
        Sort.Order.asc("id")
    );

    private final BrandPayMethodRepository brandPayMethodRepository;
    private final BrandPayTokenService brandPayTokenService;
    private final BrandPayMethodClient brandPayMethodClient;
    private final BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final PaymentPreparationService paymentPreparationService;

    /**
     * 로그인 회원의 결제수단 중 EXPIRED 상태를 제외하고 반환한다.
     * 기본 결제수단을 먼저 보여주며 서버 전용 methodKey는 응답에 포함하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PaymentMethodResponseDto> getPaymentMethods(Long memberId) {
        return brandPayMethodRepository.findAllByMemberIdAndStatusNot(
                memberId,
                PaymentsMethodStatus.EXPIRED,
                PAYMENT_METHOD_SORT
            )
            .stream()
            .map(method -> new PaymentMethodResponseDto(
                method.getId(),
                method.getProviderCode().getDisplayName(),
                method.getMaskedNumber(),
                method.getIsDefault(),
                method.getStatus()
            ))
            .toList();
    }

    /**
     * 회원 소유 결제수단을 토스에서 삭제한 뒤 로컬 상태를 EXPIRED로 변경한다.
     * 이미 EXPIRED인 결제수단은 외부 API를 다시 호출하지 않고 성공으로 처리한다.
     *
     * <p>토스 API 호출은 DB 트랜잭션 밖에서 실행한다. 토스 삭제 성공 후 로컬 저장이
     * 실패해도 같은 멱등키로 재요청하면 토스의 기존 응답을 받아 저장을 재시도할 수 있다.</p>
     */
    public void deletePaymentMethod(Long memberId, Long paymentMethodId) {
        BrandPayMethod paymentMethod = brandPayMethodRepository
            .findByIdAndMemberId(paymentMethodId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_PAY_METHOD_NOT_FOUND));

        if (paymentMethod.getStatus() == PaymentsMethodStatus.EXPIRED) {
            return;
        }

        String accessToken = brandPayTokenService.getValidAccessToken(memberId);
        String idempotencyKey = idempotencyKeyGenerator.forPaymentMethodRemoval(
            memberId, paymentMethodId, paymentMethod.getMethodKey());

        brandPayMethodClient.remove(
            accessToken,
            paymentMethod.getMethodKey(),
            paymentMethod.getType(),
            idempotencyKey
        );

        // 주문 등 기존 참조를 보존하기 위해 행을 삭제하지 않고 만료 상태로 전환한다.
        paymentMethod.expire();
        brandPayMethodRepository.saveAndFlush(paymentMethod);
    }

    public Long scheduleAutomaticPayment(Long orderId) {
        return paymentPreparationService.schedule(orderId);
    }

    /** 호환용 메서드이며 호출 완료가 승인 완료를 의미하지 않는다. */
    @Deprecated
    public void executeAutomaticPayment(Long orderId) {
        scheduleAutomaticPayment(orderId);
    }

}
