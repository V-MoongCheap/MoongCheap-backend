package com.moongcheap_backend.demand.unit.demand.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.demand.application.demand.OrderDemandService;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderDemandServiceSuccessTest {

    @Mock
    private DemandRepository demandRepository;

    @InjectMocks
    private OrderDemandService service;

    @Nested
    @DisplayName("getPaymentPendingForOrder - 성공")
    class GetPaymentPendingForOrderTest {

        @Test
        void demandBoardId로_PAYMENT_PENDING_수요_목록을_조회한다() {
            Long demandBoardId = 100L;
            Demand d1 = mock(Demand.class);
            Demand d2 = mock(Demand.class);
            when(demandRepository.findAllByDemandBoardIdAndStatus(
                demandBoardId, DemandStatus.PAYMENT_PENDING)).thenReturn(List.of(d1, d2));

            List<Demand> result = service.getPaymentPendingForOrder(demandBoardId);

            verify(demandRepository).findAllByDemandBoardIdAndStatus(
                demandBoardId, DemandStatus.PAYMENT_PENDING);
            assertThat(result).hasSize(2);
        }
    }
}
