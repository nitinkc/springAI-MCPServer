-- Schema for demo database
CREATE TABLE IF NOT EXISTS products
(
    id
    BIGINT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    name
    VARCHAR
(
    255
) NOT NULL,
    category VARCHAR
(
    100
) NOT NULL,
    price DECIMAL
(
    10,
    2
) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
