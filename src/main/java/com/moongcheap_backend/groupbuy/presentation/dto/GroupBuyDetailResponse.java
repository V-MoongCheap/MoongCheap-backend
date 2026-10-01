package com.moongcheap_backend.groupbuy.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GroupBuyDetailResponse(
    String title, //판매 페이지 이름
    String imageUrl, //상품 이미지
    Integer price, //상품 가격
    Integer targetCount, //목표 인원수
    ProductInfo productInfo
) {

    public record ProductInfo(
        Integer shippingFee, //배송비
        LocalDate deliveryDate, //배송기간
        LocalDateTime saleEndAt, //판매종료일시
        Integer totalQuantity //재고
    ) {

    }

}
