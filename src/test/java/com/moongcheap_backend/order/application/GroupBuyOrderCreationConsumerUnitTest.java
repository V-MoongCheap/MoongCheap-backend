package com.moongcheap_backend.order.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

/**
 * 테스트 대상: {@link GroupBuyOrderCreationConsumer}의 주문 생성 및 Stream ACK 처리
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyOrderCreationConsumerUnitTest {

    @Mock
    private GroupBuyOrderCreationStream orderCreationStream;

    @Mock
    private OrderService orderService;

    @Mock
    private MapRecord<String, Object, Object> record;

    @InjectMocks
    private GroupBuyOrderCreationConsumer consumer;

    @Test
    @DisplayName("해피 케이스 - 주문 생성 성공 후 메시지 ACK")
    void 주문_생성이_성공하면_ACK한다() {
        when(orderCreationStream.claimStale(anyString(), anyInt(), any()))
            .thenReturn(List.of());
        when(orderCreationStream.readNewMessage(anyString(), anyInt()))
            .thenReturn(List.of(record));
        when(orderCreationStream.getGroupBuyId(record)).thenReturn(1L);

        consumer.consume();

        verify(orderService).autoCreateOrder(1L);
        verify(orderCreationStream).acknowledge(record);
    }

    @Test
    @DisplayName("예외 케이스 - 주문 생성 실패 시 메시지를 ACK하지 않음")
    void 주문_생성이_실패하면_ACK하지_않는다() {
        when(record.getId()).thenReturn(RecordId.of("1000000000000-0"));
        when(orderCreationStream.claimStale(anyString(), anyInt(), any()))
            .thenReturn(List.of());
        when(orderCreationStream.readNewMessage(anyString(), anyInt()))
            .thenReturn(List.of(record));
        when(orderCreationStream.getGroupBuyId(record)).thenReturn(1L);
        doThrow(new IllegalStateException("DB unavailable"))
            .when(orderService).autoCreateOrder(1L);

        consumer.consume();

        verify(orderCreationStream, never()).acknowledge(record);
    }

    @Test
    @DisplayName("예외 케이스 - 잘못된 메시지는 주문 없이 ACK")
    void 필수값이_없는_메시지는_폐기한다() {
        when(record.getId()).thenReturn(RecordId.of("1000000000000-0"));
        when(orderCreationStream.claimStale(anyString(), anyInt(), any()))
            .thenReturn(List.of());
        when(orderCreationStream.readNewMessage(anyString(), anyInt()))
            .thenReturn(List.of(record));
        when(orderCreationStream.getGroupBuyId(record))
            .thenThrow(new IllegalArgumentException("groupBuyId is missing"));

        consumer.consume();

        verify(orderService, never()).autoCreateOrder(any());
        verify(orderCreationStream).acknowledge(record);
    }
}
