package com.moongcheap_backend.groupbuy.presentation;

import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyDetailResponse;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GroupBuy · 공동구매", description = "공동구매 조회 API")
@RestController
@RequestMapping("/api/group-buys")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    @Operation(
        summary = "공동구매 목록 조회",
        description = "기능 ID 없음. 공동구매 목록을 최신순으로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<Page<GroupBuyListResponse>> groupBuyList(
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return ResponseEntity.ok(groupBuyService.getAll(pageable));
    }

    @Operation(
        summary = "공동구매 상세 조회",
        description = "기능 ID 없음. 공동구매 ID로 상세 정보를 조회합니다."
    )
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<GroupBuyDetailResponse> groupBuyDetail(
        @PathVariable Long groupBuyId) {
        return ResponseEntity.ok(groupBuyService.getById(groupBuyId));
    }
}
