/* ============================================================================
 * product_award_evaluation.id 를 IDENTITY 에서 SEQUENCE 로 전환
 *
 * 배경: IDENTITY 전략은 Hibernate 의 batch INSERT 를 원천적으로 불가능하게 함.
 *       award chunk 트랜잭션에서 evaluation 을 대량 저장하는데,
 *       현재는 INSERT 마다 개별 왕복이 발생.
 *       SEQUENCE 로 전환하면 hibernate.jdbc.batch_size 설정에 의해
 *       INSERT 를 batch 로 묶어 전송할 수 있게 됨.
 * ==========================================================================*/

-- IDENTITY 제거 (소유 중인 auto-sequence 도 함께 삭제됨)
ALTER TABLE product_award_evaluation
    ALTER COLUMN id DROP IDENTITY IF EXISTS;

-- IDENTITY sequence 가 삭제된 후 새 sequence 를 생성
CREATE SEQUENCE IF NOT EXISTS product_award_evaluation_id_seq INCREMENT BY 20;

SELECT setval(
    'product_award_evaluation_id_seq',
    COALESCE((SELECT MAX(id) FROM product_award_evaluation), 1)
);

ALTER TABLE product_award_evaluation
    ALTER COLUMN id SET DEFAULT nextval('product_award_evaluation_id_seq');

ALTER SEQUENCE product_award_evaluation_id_seq OWNED BY product_award_evaluation.id;
