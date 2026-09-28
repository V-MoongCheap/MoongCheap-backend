package com.moongcheap_backend.demand.application.demand;

import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderDemandService {

    private final DemandRepository demandRepository;

    // 결제 완료 트랜잭션에 참여해 결제·주문·수요 상태를 함께 반영한다.
    @Transactional
    public void closeAfterPayment(Long demandId) {
        demandRepository.closeAfterPayment(demandId, DemandStatus.PAYMENT_PENDING,
            DemandStatus.CLOSED, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional
    public List<Demand> getPaymentPendingForOrder(Long demandBoardId) {
        return demandRepository.findAllByDemandBoardIdAndStatus(
            demandBoardId,
            DemandStatus.PAYMENT_PENDING
        );
    }
}
