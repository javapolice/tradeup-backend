CREATE TABLE trades
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    buyer_id   BIGINT       NOT NULL,
    status     VARCHAR(255) NOT NULL,

    CONSTRAINT fk_trades_product
        FOREIGN KEY (product_id)
            REFERENCES products (id),

    CONSTRAINT fk_trades_buyer
        FOREIGN KEY (buyer_id)
            REFERENCES members (id)

);
