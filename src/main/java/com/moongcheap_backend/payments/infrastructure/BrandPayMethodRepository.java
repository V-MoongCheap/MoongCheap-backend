package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandPayMethodRepository extends JpaRepository<BrandPayMethod, Long> {

    boolean existsByIdAndMemberIdAndStatus(Long id, Long memberId, PaymentsMethodStatus status);
}
