package com.moongcheap_backend.groupbuy.presentation.dto;

public record GroupBuyListResponse(
    Long groupBuyId, //공동구매 ID
    String title, //판매 페이지 이름
    String imageUrl, //상품 이미지
    Integer price //상품 가격
) {

}
