package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandPayMethodRepository extends JpaRepository<BrandPayMethod, Long> {

    boolean existsByIdAndMemberIdAndStatus(Long id, Long memberId, PaymentsMethodStatus status);

    List<BrandPayMethod> findAllByMemberId(Long memberId);

    Optional<BrandPayMethod> findByIdAndMemberId(Long id, Long memberId);

    /** 지정한 상태를 제외한 회원의 결제수단만 조회한다. */
    List<BrandPayMethod> findAllByMemberIdAndStatusNot(
        Long memberId,
        PaymentsMethodStatus excludedStatus,
        Sort sort
    );
}
