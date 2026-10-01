/* 기존 데이터에 중복 기본 결제수단이 있더라도 마이그레이션이 가능하도록 정리한다. */
UPDATE brand_pay_method
SET is_default = FALSE
WHERE is_default = TRUE
  AND status <> 'ACTIVE';

WITH ranked_defaults AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY member_id ORDER BY id) AS default_order
    FROM brand_pay_method
    WHERE is_default = TRUE
)
UPDATE brand_pay_method method
SET is_default = FALSE
FROM ranked_defaults ranked
WHERE method.id = ranked.id
  AND ranked.default_order > 1;

/* 회원당 활성 기본 결제수단은 최대 한 건만 허용한다. */
CREATE UNIQUE INDEX uq_brand_pay_method_default
    ON brand_pay_method (member_id)
    WHERE is_default = TRUE;
