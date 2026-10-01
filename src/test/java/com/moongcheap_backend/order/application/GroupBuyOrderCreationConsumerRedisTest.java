package com.moongcheap_backend.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class GroupBuyOrderCreationConsumerRedisTest {

    private static final String STREAM_KEY = "moongcheap:group-buy:order-creation";
    private static final String CONSUMER_GROUP = "group-buy-order-creators";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private GroupBuyOrderCreationStream orderCreationStream;
    private OrderService orderService;
    private GroupBuyOrderCreationConsumer consumer;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
            REDIS.getHost(),
            REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.delete(STREAM_KEY);

        orderCreationStream = new GroupBuyOrderCreationStream(redisTemplate);
        orderService = mock(OrderService.class);
        consumer = new GroupBuyOrderCreationConsumer(orderCreationStream, orderService);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(STREAM_KEY);
        connectionFactory.destroy();
    }

    @Test
    void Redis_Stream에_발행한_메시지를_처리하면_ACK하고_삭제한다() {
        orderCreationStream.publish(101L, 202L);

        assertThat(redisTemplate.opsForStream().size(STREAM_KEY)).isEqualTo(1L);

        consumer.consume();

        verify(orderService).autoCreateOrder(202L);
        assertThat(redisTemplate.opsForStream().size(STREAM_KEY)).isZero();
        assertThat(redisTemplate.opsForStream()
            .pending(STREAM_KEY, CONSUMER_GROUP)
            .getTotalPendingMessages())
            .isZero();
    }
}
