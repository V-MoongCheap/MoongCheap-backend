package com.moongcheap_backend.common.loadtest;

import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import com.moongcheap_backend.payments.application.PaymentQueueProperties;
import com.moongcheap_backend.payments.infrastructure.PaymentGatewayProperties;
import com.moongcheap_backend.payments.infrastructure.PaymentSchedule;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 실제 비즈니스 경로의 입력만 준비한다. 주문은 Consumer가 생성한다. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moongcheap.load-test.enabled", havingValue = "true")
public class OrderLoadTestService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final GroupBuyService groupBuyService;
    private final GroupBuyJudgmentSchedule judgmentSchedule;
    private final GroupBuyOrderCreationStream orderCreationStream;
    private final PaymentSchedule paymentSchedule;
    private final PaymentGatewayProperties gateway;
    private final PaymentQueueProperties queue;

    public record DemandExpected(long id, long memberId, long methodId, int quantity) { }
    public record ProductExpected(long productId, long boardId, long catalogId,
                                  List<DemandExpected> demands) { }
    public record Manifest(UUID runId, long sellerId, long sellerMemberId,
                           LocalDateTime saleEndAt, List<ProductExpected> products) { }
    public record Violation(String kind, long id, String detail) { }
    public record Report(boolean passed, int expectedOrders, int actualOrders,
                         List<Violation> violations) { }
    public record CleanupReport(UUID runId, int products, int groupBuys, int orders,
                                int demands, int members, long streamMessages) { }
    public record BulkCleanupReport(int runs, int products, int groupBuys, int orders,
                                    int demands, int members, long streamMessages) { }

    private void requireTestMode() {
        if (gateway.getMode() != PaymentGatewayProperties.Mode.MOCK || queue.isWorkerEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O 테스트에는 gateway.mode=mock, queue.worker-enabled=false가 필요합니다.");
        }
    }

    private static String marker(UUID runId) { return "load-test-order:" + runId; }

    @Transactional
    public Manifest seed(int groups, int demandsPerGroup) {
        requireTestMode();
        if (groups < 1 || groups > 1000 || demandsPerGroup < 1
            || demandsPerGroup > 10000 || (long) groups * demandsPerGroup > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "총 연결 수요는 1~10000건입니다.");
        }
        UUID runId = UUID.randomUUID();
        String tag = marker(runId);
        LocalDateTime end = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(7);
        long sellerMember = member(true);
        long seller = id("""
            insert into seller(member_id,business_name,business_number,business_number_hash,
              mail_order_registration_number,owner_name,phone_number,seller_status,approved_at)
            values (?, 'load-test', 'dummy', ?, 'load-test', 'load-test', '00000000000',
              'APPROVED', now()) returning id
            """, sellerMember, runId.toString());
        List<ProductExpected> products = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            long catalog = id("""
                insert into product_catalog(name,thumbnail_url,status)
                values (?, 'https://example.invalid/load-test.png', 'ACTIVE') returning id
                """, "load-order-" + runId + "-" + g);
            long board = id("""
                insert into demand_board(catalog_id,participant_count,price_min,price_max,status,sale_end_at)
                values (?, ?, 10000, 20000, 'GB_ACTION_REQUIRED', ?) returning id
                """, catalog, demandsPerGroup, end);
            long product = id("""
                insert into product(catalog_id,demand_board_id,seller_id,thumbnail_url,unit_price,
                  shipping_fee,delivery_date,sale_end_at,total_quantity,min_participant_count,
                  min_quantity,description,return_policy,status)
                values (?, ?, ?, 'https://example.invalid/load-test.png', 15000, 3000,
                  ?, ?, ?, 1, 1, ?, 'load-test', 'AWARDED') returning id
                """, catalog, board, seller, end.toLocalDate().plusDays(1), end,
                demandsPerGroup * 3, tag);
            List<DemandExpected> demands = new ArrayList<>();
            for (int d = 0; d < demandsPerGroup; d++) {
                long member = member(false);
                long method = id("""
                    insert into brand_pay_method(member_id,method_key,provider_code,masked_number,
                      type,is_default,status,created_at)
                    values (?, ?, 'CARD_TOSS_BANK', '0000', 'CARD', true, 'ACTIVE', now()) returning id
                    """, member, "mock_" + runId + "_" + member);
                int quantity = 1 + d % 3;
                long demand = id("""
                    insert into demand(demand_board_id,member_id,catalog_id,pay_method_id,
                      desired_price_min,desired_price_max,desire_end_at,quantity,is_substitutable,status)
                    values (?, ?, ?, ?, 10000, 20000, ?, ?, false, 'PAYMENT_PENDING') returning id
                    """, board, member, catalog, method, end, quantity);
                demands.add(new DemandExpected(demand, member, method, quantity));
            }
            products.add(new ProductExpected(product, board, catalog, List.copyOf(demands)));
        }
        return new Manifest(runId, seller, sellerMember, end, List.copyOf(products));
    }

    /** 동일 상품의 재전송은 직렬화하고, 실제 생성은 기존 서비스에 위임한다. */
    @Transactional
    public long create(UUID runId, long productId) {
        requireTestMode();
        var eligible = jdbc.queryForList("""
            select id from product where id=? and description=? for update
            """, Long.class, productId, marker(runId));
        if (eligible.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var existing = jdbc.queryForList("select id from group_buy where product_id=?",
            Long.class, productId);
        if (!existing.isEmpty()) return existing.getFirst();
        groupBuyService.createGroupBuy(productId);
        return jdbc.queryForObject("select id from group_buy where product_id=?", Long.class, productId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report verify(Manifest manifest) {
        requireTestMode();
        if (manifest == null || manifest.runId() == null || manifest.products() == null
            || manifest.products().isEmpty() || manifest.products().size() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest가 필요합니다.");
        }
        List<Violation> issues = new ArrayList<>();
        var actualProductIds = new HashSet<>(jdbc.queryForList(
            "select id from product where description=?", Long.class, marker(manifest.runId())));
        var expectedProductIds = manifest.products().stream().map(ProductExpected::productId)
            .collect(Collectors.toSet());
        if (!actualProductIds.equals(expectedProductIds)
            || expectedProductIds.size() != manifest.products().size()) {
            issues.add(new Violation("PRODUCT_SET", 0, "manifest와 실행 상품 목록 불일치"));
        }
        int expected = 0;
        int actual = 0;
        for (var product : manifest.products()) {
            if (product.demands() == null || product.demands().size() > 10000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            expected += product.demands().size();
            if (expected > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var expectedDemandIds = product.demands().stream().map(DemandExpected::id)
                .collect(Collectors.toSet());
            var actualDemandIds = new HashSet<>(jdbc.queryForList(
                "select id from demand where demand_board_id=?", Long.class, product.boardId()));
            if (!actualDemandIds.equals(expectedDemandIds)
                || expectedDemandIds.size() != product.demands().size()) {
                issues.add(new Violation("DEMAND_SET", product.productId(), "연결 수요 목록 불일치"));
            }
            var groups = jdbc.queryForList("select id, count from group_buy where product_id=?",
                product.productId());
            if (groups.size() != 1) {
                issues.add(new Violation("GROUP_BUY_COUNT", product.productId(), "count=" + groups.size()));
                continue;
            }
            long groupId = number(groups.getFirst(), "id");
            var rows = jdbc.queryForList("select * from orders where group_buy_id=?", groupId);
            actual += rows.size();
            if (number(groups.getFirst(), "count") != product.demands().size()) {
                issues.add(new Violation("PARTICIPANT_COUNT", groupId, "예상 참여 인원 불일치"));
            }
            for (var demand : product.demands()) {
                // 다른 공동구매로 잘못 연결된 주문과 전역 중복도 발견한다.
                var orders = jdbc.queryForList("select * from orders where demand_id=?", demand.id());
                if (orders.size() != 1) {
                    issues.add(new Violation("ORDER_COUNT", demand.id(), "count=" + orders.size()));
                    continue;
                }
                var row = orders.getFirst();
                if (number(row, "group_buy_id") != groupId
                    || number(row, "product_id") != product.productId()
                    || number(row, "member_id") != demand.memberId()
                    || number(row, "brandpay_id") != demand.methodId()
                    || number(row, "seller_id") != manifest.sellerId()
                    || number(row, "sum") != demand.quantity()
                    || number(row, "price") != 15000 || number(row, "delivery_fee") != 3000
                    || number(row, "total_amount") != demand.quantity() * 15000L + 3000
                    || !"PAYMENT_PENDING".equals(row.get("order_status"))) {
                    issues.add(new Violation("ORDER_CONTENT", demand.id(), "연결·금액·상태 불일치"));
                }
            }
            for (var row : rows) {
                if (!expectedDemandIds.contains(number(row, "demand_id"))) {
                    issues.add(new Violation("UNEXPECTED_ORDER", number(row, "id"), "비연결 수요의 주문"));
                }
            }
        }
        return new Report(issues.isEmpty() && actual == expected, expected, actual, List.copyOf(issues));
    }

    /** run marker로 소유권을 다시 확인하고 해당 실행 데이터만 제거한다. */
    @Transactional
    public CleanupReport cleanup(UUID runId) {
        BulkCleanupReport result = cleanupOwnedRuns(List.of(runId));
        return new CleanupReport(runId, result.products(), result.groupBuys(), result.orders(),
            result.demands(), result.members(), result.streamMessages());
    }

    /** 여러 run을 한 번에 잠그고 set 기반으로 삭제해 반복 조회와 Redis 전체 스캔을 줄인다. */
    @Transactional
    public BulkCleanupReport cleanupBulk(List<UUID> runIds) {
        return cleanupOwnedRuns(runIds);
    }

    private BulkCleanupReport cleanupOwnedRuns(List<UUID> runIds) {
        requireTestMode();
        if (runIds == null || runIds.isEmpty() || runIds.size() > 5
            || runIds.stream().anyMatch(java.util.Objects::isNull)
            || new HashSet<>(runIds).size() != runIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "서로 다른 runId를 1~5개 지정해야 합니다.");
        }
        Set<String> markers = runIds.stream().map(OrderLoadTestService::marker)
            .collect(Collectors.toSet());
        List<Map<String, Object>> ownedProducts = namedJdbc.queryForList("""
            select id, description, seller_id, demand_board_id, catalog_id
            from product where description in (:markers) order by id for update
            """, new MapSqlParameterSource("markers", markers));
        Set<String> foundMarkers = ownedProducts.stream()
            .map(row -> String.valueOf(row.get("description"))).collect(Collectors.toSet());
        if (!foundMarkers.equals(markers)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "요청한 run 중 존재하지 않는 데이터가 있습니다.");
        }
        List<Long> productIds = values(ownedProducts, "id");
        List<Long> boardIds = values(ownedProducts, "demand_board_id");
        List<Long> catalogIds = values(ownedProducts, "catalog_id");
        List<Long> sellerIds = values(ownedProducts, "seller_id");
        var productParams = new MapSqlParameterSource("productIds", productIds);
        List<Long> groupIds = namedJdbc.queryForList(
            "select id from group_buy where product_id in (:productIds)", productParams, Long.class);
        var boardParams = new MapSqlParameterSource("boardIds", boardIds);
        List<Long> demandIds = namedJdbc.queryForList(
            "select id from demand where demand_board_id in (:boardIds)", boardParams, Long.class);
        List<Long> buyerIds = namedJdbc.queryForList(
            "select member_id from demand where demand_board_id in (:boardIds)", boardParams, Long.class);
        var sellerParams = new MapSqlParameterSource("sellerIds", sellerIds);
        List<Long> sellerMemberIds = namedJdbc.queryForList(
            "select member_id from seller where id in (:sellerIds)", sellerParams, Long.class);

        Set<Long> groupSet = new HashSet<>(groupIds);
        if (!groupIds.isEmpty()) {
            var groups = new MapSqlParameterSource("ids", groupIds);
            // Consumer와 판정 작업이 끝날 때까지 기다리고 Publisher와 삭제를 직렬화한다.
            namedJdbc.queryForList("select id from group_buy where id in (:ids) for update",
                groups, Long.class);
            namedJdbc.queryForList("select id from outbox_event where aggregate_id in (:ids) for update",
                groups, Long.class);
        }
        judgmentSchedule.removeAll(groupSet);
        long streamMessages = orderCreationStream.discardForGroupBuys(groupSet);

        int orderCount = groupIds.isEmpty() ? 0 : namedJdbc.queryForObject(
            "select count(*) from orders where group_buy_id in (:ids)",
            new MapSqlParameterSource("ids", groupIds), Integer.class);
        if (!groupIds.isEmpty()) {
            var groups = new MapSqlParameterSource("ids", groupIds);
            List<Long> paymentIds = namedJdbc.queryForList("""
                select p.id from payments p join orders o on o.id=p.order_id
                where o.group_buy_id in (:ids)
                """, groups, Long.class);
            if (!paymentIds.isEmpty()) {
                paymentSchedule.removeAll(paymentIds);
                namedJdbc.update("delete from outbox_event where event_type='PAYMENT_SCHEDULE_SYNC' and aggregate_id in (:ids)",
                    new MapSqlParameterSource("ids", paymentIds));
                namedJdbc.update("delete from payments where id in (:ids)",
                    new MapSqlParameterSource("ids", paymentIds));
            }
            namedJdbc.update("delete from orders where group_buy_id in (:ids)", groups);
            namedJdbc.update("delete from group_buy_likes where group_buy_id in (:ids)", groups);
            namedJdbc.update("delete from outbox_event where aggregate_id in (:ids) and event_type in ('GROUP_BUY_ORDER_CREATION_REQUESTED','GROUP_BUY_JUDGMENT_SCHEDULED')", groups);
            namedJdbc.update("delete from group_buy where id in (:ids)", groups);
        }
        namedJdbc.update("delete from product_award_evaluation where product_id in (:productIds) or demand_board_id in (:boardIds)",
            new MapSqlParameterSource().addValue("productIds", productIds).addValue("boardIds", boardIds));
        if (!demandIds.isEmpty()) {
            namedJdbc.update("delete from reject_history where demand_id in (:ids)",
                new MapSqlParameterSource("ids", demandIds));
        }
        namedJdbc.update("delete from product where id in (:ids)",
            new MapSqlParameterSource("ids", productIds));
        namedJdbc.update("delete from demand where demand_board_id in (:boardIds)", boardParams);
        namedJdbc.update("delete from demand_board where id in (:boardIds)", boardParams);
        if (!buyerIds.isEmpty()) {
            var buyers = new MapSqlParameterSource("ids", buyerIds);
            namedJdbc.update("delete from brand_pay_method where member_id in (:ids)", buyers);
            namedJdbc.update("delete from customer_key where member_id in (:ids)", buyers);
            namedJdbc.update("delete from brand_pay_token where member_id in (:ids)", buyers);
            namedJdbc.update("delete from member where id in (:ids)", buyers);
        }
        namedJdbc.update("delete from seller_key where seller_id in (:sellerIds)", sellerParams);
        namedJdbc.update("delete from seller_payout_account where seller_id in (:sellerIds)", sellerParams);
        namedJdbc.update("delete from payout where seller_id in (:sellerIds)", sellerParams);
        namedJdbc.update("delete from seller where id in (:sellerIds)", sellerParams);
        if (!sellerMemberIds.isEmpty()) {
            namedJdbc.update("delete from member where id in (:ids)",
                new MapSqlParameterSource("ids", sellerMemberIds));
        }
        namedJdbc.update("delete from product_catalog where id in (:ids)",
            new MapSqlParameterSource("ids", catalogIds));
        return new BulkCleanupReport(runIds.size(), productIds.size(), groupIds.size(), orderCount,
            demandIds.size(), buyerIds.size() + sellerMemberIds.size(), streamMessages);
    }

    private long member(boolean seller) {
        return id("insert into member(nickname,is_seller,terms_agreed_at) values (?, ?, now()) returning id",
            "lt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 17), seller);
    }

    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private static List<Long> values(List<Map<String, Object>> rows, String field) {
        return rows.stream().map(row -> number(row, field)).distinct().toList();
    }
    private static long number(Map<String, Object> row, String field) {
        return row.get(field) instanceof Number n ? n.longValue() : -1;
    }
}
