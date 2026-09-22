package com.moongcheap_backend.demand.presentation.demandBoard;

import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingPendingResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Awarding · AI 낙찰", description = "AI 낙찰 결과 수신 (내부 API)")
@RestController
@RequestMapping("/api/awarding")
@RequiredArgsConstructor
public class AwardingController {

    private final DemandBoardService demandBoardService;

    @Operation(summary = "AI 판정 대기 보드 조회 (내부 API)",
        description = "GB_AWARDING 상태 보드 중 오래된 순으로 최대 size개를 반환합니다. "
            + "처리된 보드는 GB_AWARDING에서 빠지므로 클라이언트는 결과 없음이 나올 때까지 반복 호출하면 됩니다.")
    @GetMapping("/internal/pending")
    public AwardingPendingResponseDto getPendingAwarding(
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return demandBoardService.getPendingAwarding(size);
    }

    @Operation(summary = "AI 낙찰 결과 반영 (내부 API)",
        description = "AI가 판정한 낙찰 결과(제품별 score/reason/isAwarded)를 원자적으로 반영합니다. 내부 서비스 전용.")
    @PostMapping("/internal/result")
    public AwardingResultResponseDto applyAwardingResult(
        @RequestBody @Valid AwardingResultRequestDto request) {
        return demandBoardService.applyAwardingResult(request);
    }
}
