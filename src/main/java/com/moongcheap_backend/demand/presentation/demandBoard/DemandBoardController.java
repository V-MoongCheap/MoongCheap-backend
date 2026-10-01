package com.moongcheap_backend.demand.presentation.demandBoard;

import com.moongcheap_backend.common.response.IdResponse;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.CatalogDemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardJoinRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DemandBoard · 수요 보드", description = "수요 보드 조회")
@RestController
@RequestMapping("/api/demand-boards")
@RequiredArgsConstructor
public class DemandBoardController {

    private final DemandBoardService demandBoardService;

    @Operation(summary = "수요 보드 참가", description = "FN-B12-02. 기존 수요 보드에 참가합니다.")
    @PostMapping("/{demandBoardId}/join")
    public ResponseEntity<IdResponse> join(
        SessionPrincipal sessionPrincipal,
        @PathVariable Long demandBoardId,
        @RequestBody @Valid DemandBoardJoinRequestDto request) {
        return ResponseEntity.ok(
            IdResponse.of(
                demandBoardService.join(sessionPrincipal.memberId(), demandBoardId, request)));
    }

    @Operation(summary = "수요 보드 존재 여부 확인", description = "카탈로그 ID 기준으로 진행 중인 수요 보드(GB_GATHERING, GB_ACTION_REQUIRED)가 있는지 확인합니다.")
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> exists(
        SessionPrincipal sessionPrincipal,
        @RequestParam Long catalogId) {
        boolean exists = demandBoardService.hasProductBoard(catalogId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @Operation(summary = "상위 수요 보드 수집", description = "FN-B03-01. 마감 임박순 상위 3개 수요 보드")
    @GetMapping
    public DemandBoardListDto getHotDemandBoard(
        SessionPrincipal sessionPrincipal,
        @ParameterObject @PageableDefault(size = 3) Pageable pageable) {
        return demandBoardService.getHostDemandBoard(pageable);
    }

    @Operation(summary = "수요 보드 단건 조회", description = "FN-B12-01. 수요 보드 ID로 단건 조회합니다.")
    @GetMapping("/{demandBoardId}")
    public DemandBoardDto getById(
        SessionPrincipal sessionPrincipal,
        @PathVariable Long demandBoardId) {
        return demandBoardService.getById(sessionPrincipal.memberId(), demandBoardId);
    }

    @Operation(summary = "낙찰 결과 조회", description = "FN-B19-01. GB_ACTION_REQUIRED 상태의 수요 보드에서 본인 낙찰 결과를 조회합니다.")
    @GetMapping("/{demandBoardId}/auction-result")
    public AuctionResultDto getAuctionResult(
        SessionPrincipal sessionPrincipal,
        @PathVariable Long demandBoardId) {
        return demandBoardService.getAuctionResult(sessionPrincipal.memberId(), demandBoardId);
    }

    @Operation(summary = "상품 도감 기준 수요 보드 수집", description = "FN-B08-01. 상품 도감 ID로 참여 가능한 수요 보드를 조회합니다.")
    @GetMapping("/catalog/{catalogId}")
    public CatalogDemandBoardListDto getByCatalogId(
        SessionPrincipal sessionPrincipal,
        @RequestParam(required = false) Integer minPrice,
        @RequestParam(required = false) Integer maxPrice,
        @PathVariable Long catalogId,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return demandBoardService.getByCatalogId(
            sessionPrincipal.memberId(), catalogId, pageable, minPrice, maxPrice);
    }

    @Operation(summary = "수요 보드 편성 계획 적용 (내부 API)",
        description = "AI가 결정한 demand → demand_board 편성 계획을 원자적으로 반영합니다. 내부 서비스 전용.")
    @PostMapping("/internal/formation-plans")
    public FormationPlanResponseDto applyFormationPlan(
        @RequestBody @Valid FormationPlanRequestDto request) {
        return demandBoardService.applyFormationPlan(request);
    }

    @Operation(summary = "대체 오퍼 제안 계획 적용 (내부 API)",
        description = "AI가 결정한 대체상품 제안 계획을 반영합니다. 내부 서비스 전용.")
    @PostMapping("/internal/substitute-offer-plans")
    public SubstituteOfferPlanResponseDto applySubstituteOfferPlan(
        @RequestBody @Valid SubstituteOfferPlanRequestDto request) {
        return demandBoardService.applySubstituteOfferPlan(request);
    }
}
