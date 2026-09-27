ALTER TABLE "product_catalog"
    ADD COLUMN "category_id" BIGINT NULL;

ALTER TABLE "product_catalog"
    ADD CONSTRAINT "FK_category_TO_product_catalog"
    FOREIGN KEY ("category_id") REFERENCES "category" ("id")
    ON DELETE SET NULL;

CREATE INDEX "idx_product_catalog_category_id"
    ON "product_catalog" ("category_id");
