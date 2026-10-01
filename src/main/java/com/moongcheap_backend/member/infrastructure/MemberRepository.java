package com.moongcheap_backend.member.infrastructure;

import com.moongcheap_backend.member.domain.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginIdAndDeletedAtIsNull(String loginId);
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<Member> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);
    boolean existsByLoginIdAndDeletedAtIsNull(String loginId);
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    @Query("SELECT m.nickname FROM Member m WHERE m.nickname IN :nicknames AND m.deletedAt IS NULL")
    Set<String> findTakenNicknames(@Param("nicknames") Collection<String> nicknames);

    @Query("SELECT m.id FROM Member m WHERE m.id IN :memberIds AND m.deletedAt IS NULL")
    Set<Long> findActiveMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
