-- Non-destructive setup. Separate from the original demo and Flipkart-only tables.
CREATE DATABASE IF NOT EXISTS tulita CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE tulita;
CREATE TABLE IF NOT EXISTS multi_sources (
 source_key VARCHAR(100) PRIMARY KEY,
 store VARCHAR(20) NOT NULL,
 source_url VARCHAR(600) NOT NULL,
 license_name VARCHAR(80) NOT NULL,
 date_note VARCHAR(500) NOT NULL,
 row_count INT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS multi_records (
 record_id VARCHAR(150) PRIMARY KEY,
 source_key VARCHAR(100) NOT NULL,
 product_id VARCHAR(150) NOT NULL,
 title TEXT NOT NULL,
 category VARCHAR(500) NOT NULL,
 category_path TEXT NOT NULL,
 brand VARCHAR(300) NOT NULL,
 price DECIMAL(14,2) NULL,
 mrp DECIMAL(14,2) NULL,
 rating DECIMAL(3,2) NULL,
 observed_at VARCHAR(60) NOT NULL,
 product_url TEXT NOT NULL,
 description MEDIUMTEXT NOT NULL,
 specifications MEDIUMTEXT NOT NULL,
 availability VARCHAR(100) NOT NULL,
 variant_size TEXT NOT NULL,
 color TEXT NOT NULL,
 gender VARCHAR(150) NOT NULL,
 quantity TEXT NOT NULL,
 CONSTRAINT fk_multi_source FOREIGN KEY(source_key) REFERENCES multi_sources(source_key),
 CONSTRAINT chk_multi_price CHECK(price IS NULL OR price>=0),
 CONSTRAINT chk_multi_mrp CHECK(mrp IS NULL OR mrp>=0),
 CONSTRAINT chk_multi_rating CHECK(rating IS NULL OR rating BETWEEN 0 AND 5),
 INDEX idx_multi_source(source_key),
 INDEX idx_multi_brand(brand)
);
