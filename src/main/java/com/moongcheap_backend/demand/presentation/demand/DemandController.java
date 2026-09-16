package com.moongcheap_backend.demand.presentation.demand;

import com.moongcheap_backend.common.response.IdResponse;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.demand.application.demand.DemandService;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandCreateRequestDto;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandListDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.util.List;

@Tag(name = "Demand · 수요 관리", description = "수요 CRUD")
@RestController
@RequestMapping("/api/members/me/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @Operation(summary = "수요 등록", description = "FN-B09-04, FN-B09-01. 동일 카탈로그에 진행 중인 수요가 있으면 409.")
    @PostMapping
    public ResponseEntity<IdResponse> create(SessionPrincipal principal,
        @RequestBody @Valid DemandCreateRequestDto request) {
        return ResponseEntity.ok(
            IdResponse.of(demandService.create(request, principal.memberId())));
    }

    @Operation(summary = "나의 수요 확인(! 내용중 사용하지 않는 내용이 있다면 알려주세요)", description = "FN-B17-01. 내 수요 참여 목록 조회")
    @GetMapping
    public ResponseEntity<DemandListDto> read(SessionPrincipal principal,
        @RequestParam(required = false) List<DemandStatus> statuses,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(demandService.list(principal.memberId(), statuses, pageable));
    }

    @Operation(summary = "수요 단건 조회", description = "FN-B16-01,FN-B17-01. 수요 ID로 본인 수요를 단건 조회합니다. (모든 상태의 수요 검색)")
    @GetMapping("/{demandId}")
    public ResponseEntity<DemandListDto.DemandItemDto> get(SessionPrincipal principal,
        @PathVariable Long demandId) {
        return ResponseEntity.ok(demandService.get(principal.memberId(), demandId));
    }

    @Operation(summary = "수요 취소", description = "MVP 범위 X. 본인 수요만 취소 가능.")
    @DeleteMapping("/{demandId}")
    public ResponseEntity<Void> cancel(SessionPrincipal principal,
        @PathVariable Long demandId) {
        demandService.cancel(principal.memberId(), demandId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대체 오퍼 승낙",
        description = "FN-B16-01. SUBSTITUTE_OFFERED 상태의 본인 수요를 승낙.")
    @PatchMapping("/{demandId}/accept")
    public ResponseEntity<Void> acceptOffer(SessionPrincipal principal,
        @PathVariable Long demandId) {
        demandService.acceptOffer(principal.memberId(), demandId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대체 오퍼 거절",
        description = "FN-B16-01. SUBSTITUTE_OFFERED 상태의 본인 수요를 거절.")
    @PatchMapping("/{demandId}/reject")
    public ResponseEntity<Void> rejectOffer(SessionPrincipal principal,
        @PathVariable Long demandId) {
        demandService.rejectOffer(principal.memberId(), demandId);
        return ResponseEntity.noContent().build();
    }

}
