package com.moongcheap_backend.demand.presentation.demandBoard;

import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingPendingResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Awarding · AI 낙찰", description = "AI 낙찰 결과 수신 (내부 API)")
@RestController
@RequestMapping("/api/awarding")
@RequiredArgsConstructor
public class AwardingController {

    private final DemandBoardService demandBoardService;

    @Operation(summary = "AI 판정 대기 보드 조회 (내부 API)",
        description = "GB_AWARDING 상태의 보드와 소속 응찰(AWARDING) 상품 목록을 반환합니다. AI가 pull하여 판정에 사용합니다.")
    @GetMapping("/pending")
    public AwardingPendingResponseDto getPendingAwarding(
        @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return demandBoardService.getPendingAwarding(pageable);
    }

    @Operation(summary = "AI 낙찰 결과 반영 (내부 API)",
        description = "AI가 판정한 낙찰 결과(제품별 score/reason/isAwarded)를 원자적으로 반영합니다. 내부 서비스 전용.")
    @PostMapping("/internal/result")
    public AwardingResultResponseDto applyAwardingResult(
        @RequestBody @Valid AwardingResultRequestDto request) {
        return demandBoardService.applyAwardingResult(request);
    }
}
