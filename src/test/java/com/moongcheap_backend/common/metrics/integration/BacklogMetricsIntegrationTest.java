package com.moongcheap_backend.common.metrics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.moongcheap_backend.common.metrics.BacklogMetrics;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BacklogMetricsIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test void migrationAndRealBacklogQueriesAndStaleFailure() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        var jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='payments' and column_name='initial_scheduled_at'", Integer.class)).isEqualTo(1);
        var cf = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        cf.afterPropertiesSet();
        cf.start();
        try {
            var template = new StringRedisTemplate(cf);
            var registry = new SimpleMeterRegistry();
            var metrics = new BacklogMetrics(jdbc, template, registry);
            jdbc.update("""
                insert into outbox_event(event_type,aggregate_id,scheduled_at,status,retry_count,next_attempt_at,created_at,pending_since)
                values ('GROUP_BUY_ORDER_CREATION_REQUESTED',123,localtimestamp,'PENDING',0,localtimestamp,localtimestamp,
                  (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') - interval '30 seconds')
                """);
            template.opsForZSet().add("moongcheap:payment:execution:scheduled", "123", System.currentTimeMillis()-1000);
            template.opsForZSet().add("moongcheap:payment:execution:scheduled", "124", System.currentTimeMillis()+60000);
            var stream = new GroupBuyOrderCreationStream(template);
            stream.publish(1L, 1L);
            stream.publish(2L, 2L);
            stream.readNewMessage("test", 1);
            metrics.refresh();
            assertThat(registry.get("moongcheap.outbox.pending").tag("type", "GROUP_BUY_ORDER_CREATION_REQUESTED").gauge().value()).isEqualTo(1);
            assertThat(registry.get("moongcheap.outbox.oldest.seconds").tag("type", "GROUP_BUY_ORDER_CREATION_REQUESTED").gauge().value()).isGreaterThanOrEqualTo(29);
            assertThat(registry.get("moongcheap.queue.due").tag("queue", "payment").gauge().value()).isEqualTo(1);
            assertThat(registry.get("moongcheap.queue.size").tag("queue", "payment").gauge().value()).isEqualTo(2);
            assertThat(registry.get("moongcheap.stream.messages").tag("state", "pending").gauge().value()).isEqualTo(1);
            assertThat(registry.get("moongcheap.stream.messages").tag("state", "lag").gauge().value()).isEqualTo(1);
            assertThat(registry.get("moongcheap.collector.up").tag("source", "postgres").gauge().value()).isEqualTo(1);
            jdbc.update("update outbox_event set status='PUBLISHED'");
            metrics.refresh();
            assertThat(registry.get("moongcheap.outbox.pending").tag("type", "GROUP_BUY_ORDER_CREATION_REQUESTED").gauge().value()).isZero();
            jdbc.execute("alter table outbox_event rename to temporarily_unavailable");
            metrics.refresh();
            assertThat(registry.get("moongcheap.collector.up").tag("source", "outbox").gauge().value()).isZero();
            assertThat(registry.get("moongcheap.collector.errors").tag("source", "outbox").counter().count()).isEqualTo(1);
        } finally { cf.destroy(); }
    }
}
