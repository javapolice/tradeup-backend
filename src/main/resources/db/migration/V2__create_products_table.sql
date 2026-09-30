CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,

    CONSTRAINT fk_products_seller
      FOREIGN KEY (seller_id)
      REFERENCES members(id)
);