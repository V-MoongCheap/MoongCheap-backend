-- 썸네일이 없는 상품도 주문을 생성할 수 있도록 이미지 스냅샷을 선택값으로 변경한다.
ALTER TABLE orders ALTER COLUMN image_url DROP NOT NULL;
