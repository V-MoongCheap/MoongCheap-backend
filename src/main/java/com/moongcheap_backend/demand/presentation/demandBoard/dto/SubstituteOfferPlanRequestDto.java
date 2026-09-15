package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SubstituteOfferPlanRequestDto(
    @NotBlank String schemaVersion,
    @NotNull OffsetDateTime plannedAt,
    @NotBlank @Size(max = 200) String ruleVersion,
    @NotNull @Valid @Size(max = 100) List<Proposal> proposals
) {

    @AssertTrue(message = "proposals의 demandId는 중복될 수 없습니다")
    public boolean isDemandIdUnique() {
        if (proposals == null) {
            return true;
        }
        return proposals.stream()
            .map(Proposal::demandId)
            .distinct()
            .count() == proposals.size();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Proposal(
        @NotNull Long demandId,
        @NotNull Long expectedOriginalCatalogId,
        @NotNull Long substituteCatalogId,
        @NotNull Long demandBoardId
    ) {

        @AssertTrue(message = "expectedOriginalCatalogId와 substituteCatalogId는 서로 달라야 합니다")
        public boolean isCatalogIdDifferent() {
            if (expectedOriginalCatalogId == null || substituteCatalogId == null) {
                return true;
            }
            return !expectedOriginalCatalogId.equals(substituteCatalogId);
        }
    }
}
