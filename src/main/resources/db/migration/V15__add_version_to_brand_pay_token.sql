-- 동일 토큰을 동시에 갱신한 오래된 요청이 최신 토큰을 덮어쓰지 못하게 한다.
-- 기존 데이터는 version 0부터 시작하고 이후 값은 Hibernate의 @Version이 관리한다.
ALTER TABLE "brand_pay_token"
    ADD COLUMN "version" BIGINT NOT NULL DEFAULT 0;
