/* ============================================================================
 * product_award_evaluation.id 를 IDENTITY 에서 SEQUENCE 로 전환
 *
 * 배경: IDENTITY 전략은 Hibernate 의 batch INSERT 를 원천적으로 불가능하게 함.
 *       award chunk 트랜잭션에서 evaluation 을 대량 저장하는데,
 *       현재는 INSERT 마다 개별 왕복이 발생.
 *       SEQUENCE 로 전환하면 hibernate.jdbc.batch_size 설정에 의해
 *       INSERT 를 batch 로 묶어 전송할 수 있게 됨.
 * ==========================================================================*/

CREATE SEQUENCE product_award_evaluation_id_seq;

ALTER TABLE product_award_evaluation
    ALTER COLUMN id DROP IDENTITY IF EXISTS,
    ALTER COLUMN id SET DEFAULT nextval('product_award_evaluation_id_seq');

SELECT setval(
    'product_award_evaluation_id_seq',
    COALESCE((SELECT MAX(id) FROM product_award_evaluation), 0)
);

ALTER SEQUENCE product_award_evaluation_id_seq OWNED BY product_award_evaluation.id;
