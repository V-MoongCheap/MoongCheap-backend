ALTER TABLE "orders"
    ALTER COLUMN "phone_number" TYPE VARCHAR(512);

ALTER SEQUENCE "orders_id_seq" INCREMENT BY 20;

ALTER TABLE "orders"
    ADD COLUMN "demand_id" BIGINT NOT NULL,
    ADD CONSTRAINT "uq_orders_demand_id" UNIQUE ("demand_id"),
    ADD CONSTRAINT "FK_demand_TO_Orders"
        FOREIGN KEY ("demand_id") REFERENCES "demand" ("id");
