package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.payments.infrastructure.PaymentCancellationClient;
import com.moongcheap_backend.payments.infrastructure.PaymentGatewayException;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final Sort PAYMENT_METHOD_SORT = Sort.by(
        Sort.Order.desc("isDefault"),
        Sort.Order.asc("id")
    );

    private final BrandPayMethodRepository brandPayMethodRepository;
    private final BrandPayTokenService brandPayTokenService;
    private final BrandPayMethodClient brandPayMethodClient;
    private final BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final PaymentPreparationService paymentPreparationService;
    private final PaymentsRepository paymentsRepository;
    private final PaymentCancellationClient paymentCancellationClient;

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

    /**
     * 로그인 회원의 승인 완료 결제를 토스에서 전액 취소한다.
     *
     * <p>결제와 주문을 배타 잠금한 트랜잭션 안에서 취소 API를 호출해 배송지 입력 등
     * 주문 상태 변경과 경합하지 않게 한다. 외부 호출 동안 잠금이 유지되지만, 사용자에
     * 의해 드물게 실행되는 취소 기능의 상태 일관성을 우선한 선택이다. 요청이 타임아웃
     * 되어도 같은 멱등키로 다시 호출하므로 토스에서 중복 취소되지 않는다.</p>
     */
    @Transactional
    public void cancelPayment(Long memberId, Long paymentId, String cancelReason) {
        Payments payment = paymentsRepository
            .findByIdAndMemberIdForCancellation(paymentId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 이미 로컬 반영까지 끝난 같은 요청은 외부 API를 다시 호출하지 않는다.
        if (payment.getStatus() == PaymentsStatus.CANCELED) {
            return;
        }
        if (payment.getStatus() != PaymentsStatus.SUCCEEDED
            || payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()
            || payment.getOrders().getOrderStatus() != OrderStatus.PAYMENT_COMPLETED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANNOT_CANCEL);
        }

        String idempotencyKey = idempotencyKeyGenerator.forPaymentCancellation(
            paymentId, payment.getPaymentKey());

        PaymentCancellationClient.CancellationResponse response;
        try {
            response = paymentCancellationClient.cancel(
                payment.getPaymentKey(), cancelReason, idempotencyKey);
        } catch (PaymentGatewayException exception) {
            if (exception.kind() == PaymentGatewayException.Kind.DECLINED) {
                throw new BusinessException(ErrorCode.PAYMENT_CANNOT_CANCEL);
            }
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }

        // 다른 결제의 응답이나 전액 취소가 아닌 응답은 로컬에 반영하지 않는다.
        if (!Objects.equals(payment.getPaymentKey(), response.paymentKey())
            || !Objects.equals(payment.getOrderNo(), response.orderId())
            || !"CANCELED".equals(response.status())
            || response.canceledAt() == null
            || response.cancelReason() == null || response.cancelReason().isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }

        payment.cancel(response.cancelReason(), response.canceledAt()
            .atZoneSameInstant(ZONE_SEOUL).toLocalDateTime());
        payment.getOrders().setOrderStatus(OrderStatus.REFUNDED);
        paymentsRepository.saveAndFlush(payment);
    }

}
