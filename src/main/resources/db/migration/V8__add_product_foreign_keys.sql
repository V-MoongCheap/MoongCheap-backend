ALTER TABLE "group_buy"
    ADD CONSTRAINT "FK_product_TO_GroupBuy"
    FOREIGN KEY ("product_id") REFERENCES "product" ("id");

ALTER TABLE "orders"
    ADD CONSTRAINT "FK_product_TO_Orders"
    FOREIGN KEY ("product_id") REFERENCES "product" ("id");
