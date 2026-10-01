package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE BrandPayMethod m SET m.isDefault = false "
        + "WHERE m.member.id = :memberId AND m.isDefault = true AND m.id <> :excludeId")
    int unmarkDefaultExcept(
        @Param("memberId") Long memberId,
        @Param("excludeId") Long excludeId
    );

    /** 동기화가 기본 수단을 교체할 때 UNIQUE 제약의 중간 충돌을 피하도록 모두 해제한다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BrandPayMethod m SET m.isDefault = false "
        + "WHERE m.member.id = :memberId AND m.isDefault = true")
    int unmarkAllDefaults(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE BrandPayMethod m SET m.isDefault = true "
        + "WHERE m.id = :methodId AND m.member.id = :memberId AND m.status = :status")
    int markAsDefaultIfActive(
        @Param("methodId") Long methodId,
        @Param("memberId") Long memberId,
        @Param("status") PaymentsMethodStatus status
    );
}
