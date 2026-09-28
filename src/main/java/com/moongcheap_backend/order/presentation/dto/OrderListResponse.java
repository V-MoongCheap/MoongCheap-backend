package com.moongcheap_backend.order.presentation.dto;

import com.moongcheap_backend.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(example = """
        {
          "orderDate": "2026-08-28",
          "orderNo": "ORD_550e8400-e29b-41d4-a716-446655440000",
          "businessName": "문치프 농장",
          "orderStatus": "PAYMENT_COMPLETED",
          "imageUrl": "https://example.com/products/tangerine.jpg",
          "productName": "제주 감귤 5kg",
          "quantity": 2,
          "totalAmount": 25000
        }
        """)
public record OrderListResponse(
    LocalDate orderDate, // 주문일자
    String orderNo, // 주문번호
    String businessName, // 셀러 상호명
    OrderStatus orderStatus, // 주문 상태
    String imageUrl, // 상품 이미지 URL
    String productName, // 상품명
    Integer quantity, // 주문 수량
    Integer totalAmount // 상품 단가 × 수량 + 주문당 배송비
) {

}
