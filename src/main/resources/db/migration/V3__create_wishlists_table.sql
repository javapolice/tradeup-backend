CREATE TABLE wishlists
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    CONSTRAINT fk_wishlists_member
        FOREIGN KEY (member_id)
            REFERENCES members (id),

    CONSTRAINT fk_wishlists_product
        FOREIGN KEY (product_id)
            REFERENCES products (id),

    CONSTRAINT uk_wishlists_member_product
        UNIQUE (member_id, product_id)
);
