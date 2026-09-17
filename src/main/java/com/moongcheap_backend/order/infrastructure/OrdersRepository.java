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
}
