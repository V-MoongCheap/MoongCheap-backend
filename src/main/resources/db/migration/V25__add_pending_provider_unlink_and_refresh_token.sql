-- Google refresh token 저장을 위한 컬럼 추가 (암호화된 값 저장)
ALTER TABLE member_social
    ADD COLUMN refresh_token_enc TEXT;

-- 회원 탈퇴 시 provider unlink를 비동기 처리하기 위한 outbox 테이블
CREATE TABLE pending_provider_unlink (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    revocation_token_enc TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    dead_lettered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 워커가 (dead_lettered=false AND next_attempt_at <= now) 을 반복 조회
CREATE INDEX idx_pending_provider_unlink_next_attempt
    ON pending_provider_unlink (dead_lettered, next_attempt_at);
