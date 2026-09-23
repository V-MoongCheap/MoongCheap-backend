package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moongcheap_backend.common.util.TimeUtils;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record FormationPlanRequestDto(
    @NotBlank String schemaVersion,
    @NotNull OffsetDateTime plannedAt,
    @NotBlank String ruleVersion,
    @Valid @Size(max = 50) List<ExistingBoardAssignment> existingBoardAssignments,
    @Valid @Size(max = 50) List<NewBoard> newBoards
) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ExistingBoardAssignment(
        @NotNull Long demandBoardId,
        @NotEmpty List<Long> demandIds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record NewBoard(
        @NotBlank String clientBoardKey,
        @NotNull Long catalogId,
        @NotNull @Min(0) Integer priceMin,
        @NotNull @Min(0) Integer priceMax,
        @NotEmpty List<Long> demandIds
    ) {

        private static final int SALE_DURATION_DAYS = 5;

        public DemandBoard toEntity(boolean bypass) {
            return DemandBoard.builder()
                .catalogId(catalogId)
                .priceMin(priceMin)
                .priceMax(priceMax)
                .saleEndAt(TimeUtils.ceilToFiveMinuteMark(
                    LocalDateTime.now().plusDays(SALE_DURATION_DAYS), bypass))
                .participantCount(demandIds.size())
                .status(DemandBoardStatus.GB_GATHERING)
                .build();
        }
    }
}
