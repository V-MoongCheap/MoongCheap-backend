package com.moongcheap_backend.order.infrastructure;

import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdersRepository extends JpaRepository<Orders, Long> {

    @Query("select o.demandId from Orders o where o.demandId in :demandIds")
    List<Long> findExistingDemandIds(@Param("demandIds") Collection<Long> demandIds);

    Page<Orders> findAllByMemberId(Long memberId, Pageable pageable);

    Page<Orders> findAllByMemberIdAndOrderStatusIn(Long memberId,
        Collection<OrderStatus> orderStatuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Orders> findByOrderNoAndMemberId(String orderNo, Long memberId);

    @Query("""
        select o
        from Orders o
        join fetch o.groupBuy
        left join fetch o.brandPayMethod
        where o.orderNo = :orderNo
          and o.memberId = :memberId
        """)
    Optional<Orders> findDetailByOrderNoAndMemberId(
        @Param("orderNo") String orderNo,
        @Param("memberId") Long memberId
    );

    boolean existsByMemberIdAndOrderStatusIn(Long memberId, Collection<OrderStatus> statuses);

    @Query("""
        select o.orderStatus as orderStatus, count(o) as count
        from Orders o
        where o.memberId = :memberId
          and o.orderStatus in :statuses
        group by o.orderStatus
        """)
    List<OrderStatusCount> countByMemberIdAndOrderStatusIn(
        @Param("memberId") Long memberId,
        @Param("statuses") Collection<OrderStatus> statuses
    );

    /** 자동결제 요청에 필요한 주문과 저장된 결제수단을 함께 조회한다. */
    @Query("""
        select o
        from Orders o
        left join fetch o.brandPayMethod
        where o.id = :orderId
        """)
    Optional<Orders> findByIdForAutomaticPayment(@Param("orderId") Long orderId);

    /** 승인 결과 저장 시 같은 주문의 중복 완료를 막기 위한 배타 잠금 조회다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Orders o where o.id = :orderId")
    Optional<Orders> findByIdForPaymentUpdate(@Param("orderId") Long orderId);
}
