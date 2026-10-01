package com.moongcheap_backend.order.presentation.dto;

import com.moongcheap_backend.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(example = """
    {
      "orderDate": "2026-09-01",
      "orderNo": "ORD_550e8400-e29b-41d4-a716-446655440000",
      "product": {
        "businessName": "문치프 농장",
        "deliveryType": "택배",
        "deliveryFee": 3000,
        "orderStatus": "DELIVERED",
        "imageUrl": "https://example.com/products/tangerine.jpg",
        "productName": "제주 감귤 5kg",
        "quantity": 2,
        "productAmount": 25000,
        "groupBuy": {
          "title": "제주 감귤 공동구매",
          "targetCount": 100,
          "currentCount": 100,
          "endAt": "2026-08-25T23:59:59"
        },
        "availableActions": ["TRACK_SHIPMENT", "CONFIRM_RECEIPT"]
      },
      "shipping": {
        "recipientNameMasked": "홍*동",
        "phoneNumberMasked": "010-****-5678",
        "addressMasked": "서울특별시 강남구 테헤란로 ***"
      },
      "payment": {
        "productAmount": 25000,
        "deliveryFee": 3000,
        "totalPaymentAmount": 28000,
        "paymentMethod": "신한카드 ****-1234"
      }
    }
    """)
public record OrderDetailResponse(
    LocalDate orderDate, // 주문일자
    String orderNo, // 주문번호
    ProductInfo product, // 상품 카드 정보
    ShippingInfo shipping, // 배송 정보
    PaymentInfo payment // 결제 내역
) {

    // 상품 카드
    public record ProductInfo(
        String businessName, // 셀러 상호명
        String deliveryType, // 배송 유형
        Integer deliveryFee, // 배송비
        OrderStatus orderStatus, // 주문 상태
        String imageUrl, // 상품 이미지 URL
        String productName, // 상품명
        Integer quantity, // 주문 수량
        Integer productAmount, // 상품 금액
        GroupBuyInfo groupBuy // 공동구매 정보
    ) {

    }

    // 공동구매 정보
    public record GroupBuyInfo(
        Long groupBuyId, //공동구매 id
        String title // 공동구매명
    ) {

    }

    // 마스킹된 배송지 정보
    public record ShippingInfo(
        String recipientNameMasked, // 마스킹된 수령인명
        String phoneNumberMasked, // 마스킹된 휴대폰 번호
        String addressMasked // 마스킹된 배송 주소
    ) {

    }

    // 결제 내역
    public record PaymentInfo(
        Integer productAmount, // 상품 금액
        Integer deliveryFee, // 배송비
        Integer totalPaymentAmount, // 총 결제 금액
        String paymentMethod // 결제수단
    ) {

    }
}
