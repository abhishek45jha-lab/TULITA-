CREATE DATABASE IF NOT EXISTS tulita CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE tulita;
CREATE TABLE IF NOT EXISTS photo_products (
 id VARCHAR(80) PRIMARY KEY, name VARCHAR(500) NOT NULL, brand VARCHAR(200) NOT NULL,
 model VARCHAR(200) NOT NULL, variant VARCHAR(500) NOT NULL, category VARCHAR(100) NOT NULL, review_note TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS photo_offers (
 product_id VARCHAR(80) NOT NULL, store VARCHAR(20) NOT NULL, price DECIMAL(14,2) NOT NULL,
 currency CHAR(3) NOT NULL, source_title TEXT NOT NULL, source_url VARCHAR(1500) NOT NULL,
 observed_at VARCHAR(60) NOT NULL, source_file VARCHAR(150) NOT NULL, source_row INT NOT NULL, source_side INT NOT NULL,
 source_row_sha256 CHAR(64) NOT NULL, specifications MEDIUMTEXT NOT NULL, description MEDIUMTEXT NOT NULL,
 PRIMARY KEY(product_id,store), FOREIGN KEY(product_id) REFERENCES photo_products(id) ON DELETE CASCADE,
 CHECK(price>=0), CHECK(currency='USD'), CHECK(store IN ('Amazon','Walmart','Target','Sears'))
);
CREATE TABLE IF NOT EXISTS photo_images (
 product_id VARCHAR(80) NOT NULL, relative_path VARCHAR(500) NOT NULL, source_url TEXT NOT NULL,
 sha256 CHAR(64) NOT NULL, store VARCHAR(20) NOT NULL, PRIMARY KEY(product_id,relative_path),
 FOREIGN KEY(product_id) REFERENCES photo_products(id) ON DELETE CASCADE
);
