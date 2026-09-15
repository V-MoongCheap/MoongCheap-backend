package com.moongcheap_backend.demand.infrastructure.demandBoard;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingPendingResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.CatalogDemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardSummaryDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DemandBoardQueryRepositoryImpl implements DemandBoardQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<DemandBoardSummaryDto> MAPPER = (rs, rowNum) -> new DemandBoardSummaryDto(
        rs.getLong("board_id"),
        rs.getLong("catalog_id"),
        rs.getString("catalog_thumbnail_url"),
        rs.getString("catalog_name"),
        rs.getInt("participant_count"),
        rs.getInt("seller_count"),
        rs.getObject("board_price_min", Integer.class),
        rs.getObject("board_price_max", Integer.class),
        rs.getObject("board_sale_end_at", LocalDateTime.class)
    );

    private static final String SELECT_COLUMNS = """
            pc.id            AS catalog_id,
            pc.name          AS catalog_name,
            pc.thumbnail_url AS catalog_thumbnail_url,
            pc.list_price    AS catalog_list_price,
            d.id             AS board_id,
            d.participant_count,
            d.price_min      AS board_price_min,
            d.price_max      AS board_price_max,
            d.sale_end_at    AS board_sale_end_at,
            (
                SELECT COUNT(*)
                FROM product p
                WHERE p.demand_board_id = d.id
                  AND p.status = 'BIDDING'
            )                AS seller_count
        """;

    private static final String SELECT_CLAUSE = """
        SELECT
        """ + SELECT_COLUMNS + """
        FROM (
            SELECT *
            FROM demand_board
            WHERE %s
              AND sale_end_at > CURRENT_TIMESTAMP
            ORDER BY sale_end_at ASC, id ASC
            LIMIT :limit OFFSET :offset
        ) d
        INNER JOIN product_catalog pc ON d.catalog_id = pc.id
        """;

    private static final String BY_ID_CLAUSE = """
        SELECT
        """ + SELECT_COLUMNS + """
        FROM demand_board d
        INNER JOIN product_catalog pc ON d.catalog_id = pc.id
        WHERE d.id = :demand_board_id
        """;

    @Override
    public List<DemandBoardSummaryDto> getDemandBoardItems(
        List<DemandBoardStatus> statuses, Pageable pageable) {
        String inClause = statuses.stream()
            .map(s -> "'" + s.name() + "'")
            .collect(Collectors.joining(", "));
        return jdbcTemplate.query(
            String.format(SELECT_CLAUSE, "status IN (" + inClause + ")"),
            Map.of("limit", pageable.getPageSize(), "offset", pageable.getOffset()),
            MAPPER
        );
    }

    @Override
    public Optional<DemandBoardSummaryDto> getDemandBoardItemsById(Long demandBoardId) {
        return jdbcTemplate.query(
            BY_ID_CLAUSE,
            Map.of("demand_board_id", demandBoardId),
            MAPPER
        ).stream().findFirst();
    }

    private static final RowMapper<CatalogDemandBoardListDto.DemandBoardCardDto> CATALOG_MAPPER =
        (rs, rowNum) -> new CatalogDemandBoardListDto.DemandBoardCardDto(
            rs.getLong("id"),
            rs.getInt("participant_count"),
            rs.getInt("seller_count"),
            rs.getObject("price_min", Integer.class),
            rs.getObject("price_max", Integer.class),
            rs.getObject("sale_end_at", LocalDateTime.class),
            rs.getBoolean("is_participating")
        );

    private static final String BY_CATALOG_QUERY = """
        SELECT
            db.id,
            db.participant_count,
            db.price_min,
            db.price_max,
            db.sale_end_at,
            (
                SELECT COUNT(*) FROM product p
                WHERE p.demand_board_id = db.id AND p.status = 'BIDDING'
            ) AS seller_count,
            EXISTS (
                SELECT 1 FROM demand d
                WHERE d.demand_board_id = db.id
                  AND d.member_id = :memberId
                  AND d.status IN ('ASSIGNED', 'PAYMENT_PENDING')
            ) AS is_participating
        FROM (
            SELECT *
            FROM demand_board
            WHERE catalog_id = :catalogId
              AND status = 'GB_GATHERING'
              AND sale_end_at > CURRENT_TIMESTAMP
              AND (:minPrice IS NULL OR price_max > :minPrice)
              AND (:maxPrice IS NULL OR price_min < :maxPrice)
            ORDER BY sale_end_at ASC, id ASC
            LIMIT :limit OFFSET :offset
        ) db
        """;

    /**
     * demand_board의 sale_end_at, id 정렬을 quicksort로 진행하지만 도감 하나당 수요가 많아도 100개를 넘지 않을것으로 예상함으로 따로
     * index를 두지 않음
     */
    @Override
    public List<CatalogDemandBoardListDto.DemandBoardCardDto> getDemandBoardsByCatalogId(
        Long catalogId, Long memberId, Pageable pageable, Integer minPrice, Integer maxPrice) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("catalogId", catalogId)
            .addValue("memberId", memberId)
            .addValue("limit", pageable.getPageSize())
            .addValue("offset", pageable.getOffset())
            .addValue("minPrice", minPrice)
            .addValue("maxPrice", maxPrice);
        return jdbcTemplate.query(BY_CATALOG_QUERY, params, CATALOG_MAPPER);
    }

    private static final String AUCTION_RESULT_QUERY = """
        SELECT
            d.status,
            d.quantity,
            db.participant_count,
            db.judged_at,
            pc.name             AS catalog_name,
            awarded.thumbnail_url AS thumbnail_url,
            awarded.unit_price,
            awarded.shipping_fee,
            awarded.seller_name,
            awarded.award_reason,
            (
                SELECT SUM(d2.quantity)
                FROM demand d2
                WHERE d2.demand_board_id = db.id
                  AND d2.status IN ('ASSIGNED', 'PAYMENT_PENDING', 'CLOSED')
            ) AS total_participant_quantity
        FROM demand d
        INNER JOIN demand_board db ON d.demand_board_id = db.id
                                  AND db.status = 'GB_ACTION_REQUIRED'
        INNER JOIN product_catalog pc ON d.catalog_id = pc.id
        LEFT  JOIN LATERAL (
            SELECT p.seller_id,
                   p.thumbnail_url,
                   p.unit_price,
                   p.shipping_fee,
                   pae.reason AS award_reason,
                   s.business_name AS seller_name
            FROM product p
            LEFT  JOIN product_award_evaluation pae ON pae.product_id = p.id
            LEFT  JOIN seller s ON s.id = p.seller_id
            WHERE p.demand_board_id = db.id
              AND p.status IN ('AWARDED', 'ON_SALE')
            LIMIT 1
        ) awarded ON true
        WHERE d.demand_board_id = :demandBoardId
          AND d.member_id = :memberId
          AND d.status IN ('ASSIGNED', 'PAYMENT_PENDING', 'CLOSED')
        """;

    private static final RowMapper<AuctionResultDto> AUCTION_RESULT_MAPPER = (rs, rowNum) ->
        AuctionResultDto.of(
            DemandStatus.valueOf(rs.getString("status")),
            rs.getString("catalog_name"),
            rs.getString("thumbnail_url"),
            rs.getObject("unit_price", Integer.class),
            rs.getObject("shipping_fee", Integer.class),
            rs.getString("seller_name"),
            rs.getObject("quantity", Integer.class),
            rs.getObject("participant_count", Integer.class),
            rs.getObject("total_participant_quantity", Long.class),
            rs.getObject("judged_at", LocalDateTime.class),
            rs.getString("award_reason")
        );

    @Override
    public Optional<AuctionResultDto> getAuctionResult(Long demandBoardId, Long memberId) {
        return jdbcTemplate.query(
            AUCTION_RESULT_QUERY,
            Map.of("demandBoardId", demandBoardId, "memberId", memberId),
            AUCTION_RESULT_MAPPER
        ).stream().findFirst();
    }

    private static final String PENDING_AWARDING_BOARDS_QUERY = """
        SELECT
            db.id             AS board_id,
            db.catalog_id     AS catalog_id,
            db.price_min      AS price_min,
            db.price_max      AS price_max,
            db.sale_end_at    AS sale_end_at,
            db.updated_at     AS calculation_started_at,
            db.participant_count AS participant_count,
            COALESCE((
                SELECT SUM(d.quantity)
                FROM demand d
                WHERE d.demand_board_id = db.id
                  AND d.status = 'ASSIGNED'
            ), 0)             AS total_quantity
        FROM demand_board db
        WHERE db.status = 'GB_AWARDING'
        ORDER BY db.updated_at ASC, db.id ASC
        LIMIT :limit
        """;

    private static final String PENDING_AWARDING_PRODUCTS_QUERY = """
        SELECT
            p.id              AS product_id,
            p.demand_board_id AS board_id,
            p.seller_id       AS seller_id,
            p.unit_price      AS price,
            p.total_quantity  AS quantity
        FROM product p
        WHERE p.demand_board_id IN (:boardIds)
          AND p.status = 'AWARDING'
        ORDER BY p.id ASC
        """;

    @Override
    public List<AwardingPendingResponseDto.Board> getPendingAwardingBoards(int fetchSize) {
        List<PendingBoardRow> boardRows = jdbcTemplate.query(
            PENDING_AWARDING_BOARDS_QUERY,
            Map.of("limit", fetchSize),
            (rs, rowNum) -> new PendingBoardRow(
                rs.getLong("board_id"),
                rs.getLong("catalog_id"),
                rs.getObject("price_min", Integer.class),
                rs.getObject("price_max", Integer.class),
                rs.getObject("sale_end_at", LocalDateTime.class),
                rs.getObject("calculation_started_at", LocalDateTime.class),
                rs.getInt("participant_count"),
                rs.getLong("total_quantity")
            )
        );

        if (boardRows.isEmpty()) {
            return List.of();
        }

        List<Long> boardIds = boardRows.stream().map(PendingBoardRow::boardId).toList();
        Map<Long, List<AwardingPendingResponseDto.Product>> productsByBoard = new HashMap<>();
        jdbcTemplate.query(
            PENDING_AWARDING_PRODUCTS_QUERY,
            new MapSqlParameterSource("boardIds", boardIds),
            (rs, rowNum) -> {
                Long boardId = rs.getLong("board_id");
                AwardingPendingResponseDto.Product product = new AwardingPendingResponseDto.Product(
                    rs.getLong("product_id"),
                    rs.getLong("seller_id"),
                    rs.getObject("price", Integer.class),
                    rs.getObject("quantity", Integer.class)
                );
                productsByBoard.computeIfAbsent(boardId, k -> new ArrayList<>()).add(product);
                return null;
            }
        );

        return boardRows.stream()
            .map(row -> new AwardingPendingResponseDto.Board(
                row.boardId(),
                row.catalogId(),
                row.priceMin(),
                row.priceMax(),
                row.saleEndAt(),
                row.calculationStartedAt(),
                row.participantCount(),
                row.totalQuantity(),
                productsByBoard.getOrDefault(row.boardId(), List.of())
            ))
            .toList();
    }

    private record PendingBoardRow(
        Long boardId,
        Long catalogId,
        Integer priceMin,
        Integer priceMax,
        LocalDateTime saleEndAt,
        LocalDateTime calculationStartedAt,
        int participantCount,
        Long totalQuantity
    ) {

    }
}
