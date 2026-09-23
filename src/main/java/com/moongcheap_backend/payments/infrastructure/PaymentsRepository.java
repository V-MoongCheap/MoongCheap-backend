package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentsRepository extends JpaRepository<Payments, Long> {

    Optional<Payments> findFirstByOrdersIdOrderByIdDesc(Long orderId);

    @Query(value = "select current_timestamp", nativeQuery = true)
    Instant databaseNow();

    @Query(value = "select set_config('lock_timeout', '1s', true)", nativeQuery = true)
    String configureLockTimeout();

    @Query("select p.orders.id from Payments p where p.id = :paymentId")
    Optional<Long> findOrderId(@Param("paymentId") Long paymentId);

    Optional<Payments> findFirstByOrdersIdAndStatusInOrderByIdDesc(
        Long orderId,
        Collection<PaymentsStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payments p where p.id = :paymentId")
    Optional<Payments> findByIdForUpdate(@Param("paymentId") Long paymentId);

    /** 취소 중 상태 경합을 막고 회원 소유 결제만 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from Payments p
        join fetch p.orders o
        where p.id = :paymentId and o.memberId = :memberId
        """)
    Optional<Payments> findByIdAndMemberIdForCancellation(
        @Param("paymentId") Long paymentId,
        @Param("memberId") Long memberId
    );
}
