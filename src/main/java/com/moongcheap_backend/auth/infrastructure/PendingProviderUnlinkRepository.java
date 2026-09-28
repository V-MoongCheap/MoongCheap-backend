package com.moongcheap_backend.auth.infrastructure;

import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingProviderUnlinkRepository
    extends JpaRepository<PendingProviderUnlink, Long> {

    /**
     * dead_lettered 되지 않았고 next_attempt_at 이 지금 이전인 row 를 chunk 만큼 잠금 확보.
     * 다른 pod 이 동시에 이 함수를 호출해도 SKIP LOCKED 로 각자 다른 row 를 가져간다.
     */
    @Query(value = """
        SELECT * FROM pending_provider_unlink
         WHERE dead_lettered = false
           AND next_attempt_at <= :now
         ORDER BY next_attempt_at ASC
         LIMIT :chunkSize
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<PendingProviderUnlink> selectClaimable(
        @Param("now") LocalDateTime now,
        @Param("chunkSize") int chunkSize);
}
