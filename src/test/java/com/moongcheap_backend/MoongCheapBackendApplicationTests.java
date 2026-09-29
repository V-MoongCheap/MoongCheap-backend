package com.moongcheap_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import java.util.ArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MoongCheapBackendApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void 주문_이미지_컬럼은_null을_허용한다() throws Exception {
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(
                 connection.getCatalog(), connection.getSchema(), "orders", "image_url")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt("NULLABLE"))
                .isEqualTo(java.sql.DatabaseMetaData.columnNullable);
        }
    }

    @Test
    void 결제기록없는_대기주문만_보정하고_배송비를_중복합산하지_않는다() throws Exception {
        // 연결별 임시 테이블에서 보정 SQL을 검증하고 롤백해 공용 테스트 DB를 보존한다.
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TEMP TABLE orders (
                        id BIGINT PRIMARY KEY, price INTEGER, sum INTEGER, delivery_fee INTEGER,
                        total_amount INTEGER, order_status TEXT, updated_at TIMESTAMPTZ);
                    CREATE TEMP TABLE payments (order_id BIGINT, payments_status TEXT);
                    INSERT INTO orders VALUES
                        (1, 4500, 1, 3000, 4500, 'PAYMENT_PENDING', NULL),
                        (2, 4500, 5, 3000, 22500, 'PAYMENT_PENDING', NULL),
                        (3, 4500, 1, 3000, 7500, 'PAYMENT_PENDING', NULL),
                        (4, 4500, 1, 3000, 4500, 'PAYMENT_COMPLETED', NULL),
                        (5, 4500, 1, 3000, 4500, 'PAYMENT_PENDING', NULL),
                        (6, 4500, 1, 3000, 4500, 'PAYMENT_PENDING', NULL),
                        (7, 4500, 1, 0, 4500, 'PAYMENT_PENDING', NULL);
                    INSERT INTO payments VALUES (5, 'PENDING'), (6, 'UNKNOWN');
                    """);
                var script = new ClassPathResource(
                    "db/migration/V24__include_delivery_fee_in_unpaid_orders.sql");
                ScriptUtils.executeSqlScript(connection, script);
                ScriptUtils.executeSqlScript(connection, script);
                try (var rows = statement.executeQuery("SELECT total_amount FROM orders ORDER BY id")) {
                    var amounts = new ArrayList<Integer>();
                    while (rows.next()) amounts.add(rows.getInt(1));
                    assertThat(amounts).containsExactly(7500, 25500, 7500, 4500, 4500, 4500, 4500);
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
