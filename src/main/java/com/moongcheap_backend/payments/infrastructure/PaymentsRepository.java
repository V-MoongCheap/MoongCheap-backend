package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentsRepository extends JpaRepository<Payments, Long> {

    Optional<Payments> findFirstByOrdersIdAndStatusInOrderByIdDesc(
        Long orderId,
        Collection<PaymentsStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payments p where p.id = :paymentId")
    Optional<Payments> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
