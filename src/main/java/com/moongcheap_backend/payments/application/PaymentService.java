package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.CustomerKey;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentRequest;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.CustomerKeyRepository;
import com.moongcheap_backend.payments.application.PaymentPreparationService.Preparation;
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
    private final OrdersRepository ordersRepository;
    private final CustomerKeyRepository customerKeyRepository;
    private final BrandPayPaymentClient brandPayPaymentClient;
    private final PaymentPreparationService paymentPreparationService;
    private final PaymentCompletionService paymentCompletionService;

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

    /**
     * 주문에 저장된 결제수단으로 브랜드페이 자동결제를 실행한다.
     * 자동결제 API는 고객 Access Token이 아니라 서버 시크릿 키로 인증한다.
     *
     * <p>외부 API 호출 중 DB 트랜잭션을 점유하지 않는다. 호출 성공 후에는
     * {@link PaymentCompletionService}가 결제 이력과 주문 상태를 원자적으로 반영한다.
     * 장애로 재시도하더라도 주문번호 기반 멱등키가 같으므로 중복 승인을 방지한다.</p>
     */
    public void executeAutomaticPayment(Long orderId) {
        Orders order = ordersRepository.findByIdForAutomaticPayment(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 완료된 주문에 대해서는 토스 API를 다시 호출하지 않는다.
        if (order.getOrderStatus() == OrderStatus.PAYMENT_COMPLETED) {
            return;
        }
        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.BRAND_PAY_AUTO_PAYMENT_NOT_ALLOWED);
        }

        BrandPayMethod paymentMethod = order.getBrandPayMethod();
        validateAutomaticPaymentMethod(order, paymentMethod);

        CustomerKey customerKey = customerKeyRepository.findById(order.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_KEY_NOT_FOUND));
        String idempotencyKey = idempotencyKeyGenerator.forAutomaticPayment(
            order.getOrderNo());

        PaymentsMethod method = paymentMethod.getType() == PaymentType.CARD
            ? PaymentsMethod.CARD
            : PaymentsMethod.TRANSFER;
        // 외부 API를 호출하기 전에 READY 결제 이력을 커밋해 요청 시도를 추적한다.
        Preparation preparation = paymentPreparationService.prepare(orderId, method);
        if (!preparation.requestRequired()) {
            return;
        }

        AutomaticPaymentResponse response = brandPayPaymentClient.pay(
            new AutomaticPaymentRequest(
                customerKey.getCustomerKey(),
                paymentMethod.getMethodKey(),
                paymentMethod.getType(),
                order.getTotalAmount(),
                order.getOrderNo(),
                order.getProductName()
            ),
            idempotencyKey
        );
        validateAutomaticPaymentResponse(order, response);

        paymentCompletionService.completeAutomaticPayment(
            orderId, preparation.paymentId(), response);
    }

    private void validateAutomaticPaymentMethod(Orders order,
        BrandPayMethod paymentMethod) {
        if (paymentMethod == null
            || !paymentMethod.getMember().getId().equals(order.getMemberId())
            || paymentMethod.getStatus() != PaymentsMethodStatus.ACTIVE
            || paymentMethod.getMethodKey() == null
            || paymentMethod.getMethodKey().isBlank()
            || order.getTotalAmount() == null
            || order.getTotalAmount() <= 0
            || order.getProductName() == null
            || order.getProductName().isBlank()
            || order.getProductName().length() > 100) {
            throw new BusinessException(ErrorCode.BRAND_PAY_AUTO_PAYMENT_NOT_ALLOWED);
        }
    }

    /** 토스 응답이 요청한 주문과 정확히 일치할 때만 로컬에서 결제 완료 처리한다. */
    private void validateAutomaticPaymentResponse(Orders order,
        AutomaticPaymentResponse response) {
        if (response == null
            || !order.getOrderNo().equals(response.orderId())
            || !order.getProductName().equals(response.orderName())
            || order.getTotalAmount() != response.totalAmount()
            || !"DONE".equals(response.status())) {
            throw new BusinessException(ErrorCode.BRAND_PAY_AUTO_PAYMENT_FAILED);
        }
    }

}
