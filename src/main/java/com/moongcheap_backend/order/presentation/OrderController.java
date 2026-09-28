package com.moongcheap_backend.order.presentation;

import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.order.application.OrderService;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse;
import com.moongcheap_backend.order.presentation.dto.OrderListResponse;
import com.moongcheap_backend.order.presentation.dto.OrderShippingAddressRequest;
import com.moongcheap_backend.order.presentation.dto.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "주문 관련 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    //주문목록조회
    @Operation(
        summary = "주문 목록 조회",
        description = "탭별로 내 주문 목록을 조회합니다. 탭을 생략하면 전체 주문을 조회하며, 기본 페이지 크기는 20개입니다."
    )
    @GetMapping("/list")
    public ResponseEntity<Page<OrderListResponse>> orderList(SessionPrincipal principal,
        @RequestParam(defaultValue = "ALL") OrderListTab tab,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return ResponseEntity.ok(orderService.viewOrderList(principal.memberId(), tab, pageable));
    }

    @Operation(summary = "마이페이지 주문 진행 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<OrderSummaryResponse> getOrderSummary(SessionPrincipal principal) {
        return ResponseEntity.ok(orderService.getSummary(principal.memberId()));
    }

    //주문상세조회
    @Operation(summary = "주문 상세 조회")
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderDetailResponse> orderDetail(SessionPrincipal principal,
        @PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.viewOrderDetail(principal.memberId(), orderNo));
    }

    //주문취소
    @Operation(summary = "주문 취소")
    @PatchMapping("/{orderNo}/cancel")
    public ResponseEntity<Void> orderCancel(SessionPrincipal principal,
        @PathVariable String orderNo) {
        orderService.orderCancel(principal.memberId(), orderNo);
        return ResponseEntity.noContent().build();
    }

    //배송지 입력
    @Operation(summary = "배송지 입력")
    @PostMapping("/{orderNo}/shipping-address")
    public ResponseEntity<OrderDetailResponse> editShippingAddress(SessionPrincipal principal,
        @PathVariable String orderNo,
        @RequestBody @Valid OrderShippingAddressRequest request) {
        orderService.
            updateShippingAddress(principal.memberId(), orderNo, request);
        return ResponseEntity.ok(orderService.
            viewOrderDetail(principal.memberId(), orderNo));
    }

    public enum OrderListTab {
        ALL,
        IN_PROGRESS,
        DELIVERED,
        COMPLETED
    }

    //구매 확정
}
