package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AwardingResultRequestDto(
    @NotBlank String schemaVersion,
    @NotNull OffsetDateTime plannedAt,
    @NotBlank @Size(max = 200) String ruleVersion,
    @NotNull @Valid @NotEmpty @Size(max = 50) List<BoardResult> results
) {

    @AssertTrue(message = "results의 boardId는 중복될 수 없습니다")
    public boolean isBoardIdUnique() {
        if (results == null) {
            return true;
        }
        return results.stream()
            .map(BoardResult::boardId)
            .distinct()
            .count() == results.size();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record BoardResult(
        @NotNull Long boardId,
        @NotNull LocalDateTime judgedAt,
        @NotNull @Valid @NotEmpty List<Evaluation> evaluations
    ) {

        @AssertTrue(message = "evaluations의 productId는 중복될 수 없습니다")
        public boolean isProductIdUnique() {
            if (evaluations == null) {
                return true;
            }
            return evaluations.stream()
                .map(Evaluation::productId)
                .distinct()
                .count() == evaluations.size();
        }

        @AssertTrue(message = "낙찰(isAwarded=true) 항목은 최대 1개까지 허용됩니다")
        public boolean isAwardedAtMostOne() {
            if (evaluations == null) {
                return true;
            }
            return evaluations.stream()
                .filter(e -> Boolean.TRUE.equals(e.isAwarded()))
                .count() <= 1;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Evaluation(
        @NotNull Long productId,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal score,
        @Size(max = 500) String reason,
        @NotNull Boolean isAwarded
    ) {

    }
}
