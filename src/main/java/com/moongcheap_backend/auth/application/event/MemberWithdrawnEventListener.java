package com.moongcheap_backend.auth.application.event;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 트랜잭션 커밋 이후에 세션 무효화(Redis I/O)를 수행한다.
 * 트랜잭션 안에서 외부 I/O를 잡지 않기 위한 분리.
 */
@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventListener {

    private final AuthSessionManager sessionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawn(MemberWithdrawnEvent event) {
        sessionManager.invalidateAllForMember(event.memberId());
    }
}
