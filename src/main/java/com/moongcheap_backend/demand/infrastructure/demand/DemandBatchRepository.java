package com.moongcheap_backend.demand.infrastructure.demand;

import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.ExistingBoardAssignment;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DemandBatchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * assignToExistingBoard 의 batch 버전.
     * demandIds 는 PostgreSQL bigint[] 배열 파라미터로 전달하여 batch 크기가 달라도 SQL 이 고정된다.
     */
    private static final String ASSIGN_TO_EXISTING_BOARD_SQL = """
        WITH board_update AS (
            UPDATE demand_board
               SET participant_count = participant_count + :count
             WHERE id = :boardId AND status = 'GB_GATHERING'
            RETURNING sale_end_at
        ),
        locked_demands AS (
            SELECT id
              FROM demand
             WHERE id = ANY(:demandIds)
               AND status = 'UNASSIGNED'
             ORDER BY id
             FOR UPDATE
        )
        UPDATE demand
           SET status = 'ASSIGNED',
               demand_board_id = :boardId,
               desire_end_at = (SELECT sale_end_at FROM board_update),
               updated_at = :updatedAt
         WHERE id IN (SELECT id FROM locked_demands)
           AND EXISTS (SELECT 1 FROM board_update)
        """;

    public int[] batchAssignToExistingBoard(
        List<ExistingBoardAssignment> assignments,
        LocalDateTime updatedAt) {
        MapSqlParameterSource[] params = assignments.stream()
            .map(a -> new MapSqlParameterSource()
                .addValue("boardId", a.demandBoardId())
                .addValue("count", a.demandIds().size())
                .addValue("updatedAt", updatedAt)
                .addValue("demandIds", a.demandIds().toArray(new Long[0])))
            .toArray(MapSqlParameterSource[]::new);
        return jdbc.batchUpdate(ASSIGN_TO_EXISTING_BOARD_SQL, params);
    }

    /**
     * assignToBoard 의 batch 버전.
     */
    private static final String ASSIGN_TO_BOARD_SQL = """
        WITH locked_demands AS (
            SELECT id
              FROM demand
             WHERE id = ANY(:demandIds)
               AND status = 'UNASSIGNED'
             ORDER BY id
             FOR UPDATE
        )
        UPDATE demand
           SET status = 'ASSIGNED',
               demand_board_id = :boardId,
               desire_end_at = :desireEndAt,
               updated_at = :updatedAt
         WHERE id IN (SELECT id FROM locked_demands)
        """;

    public int[] batchAssignToBoard(
        List<AssignToBoardArgs> args,
        LocalDateTime updatedAt) {
        MapSqlParameterSource[] params = args.stream()
            .map(a -> new MapSqlParameterSource()
                .addValue("boardId", a.boardId())
                .addValue("desireEndAt", a.desireEndAt())
                .addValue("updatedAt", updatedAt)
                .addValue("demandIds", a.demandIds().toArray(new Long[0])))
            .toArray(MapSqlParameterSource[]::new);
        return jdbc.batchUpdate(ASSIGN_TO_BOARD_SQL, params);
    }

    public record AssignToBoardArgs(Long boardId, LocalDateTime desireEndAt, List<Long> demandIds) {

    }
}
