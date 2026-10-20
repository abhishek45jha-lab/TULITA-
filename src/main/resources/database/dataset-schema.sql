-- Separate historical tables: does NOT delete or alter the 20-product demo tables.
-- Import once as schema administrator, then run the Java CSV importer.
CREATE DATABASE IF NOT EXISTS tulita CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE tulita;
CREATE TABLE IF NOT EXISTS dataset_sources (
  source_key VARCHAR(80) PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  source_url VARCHAR(600) NOT NULL,
  license_name VARCHAR(80) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  row_count INT NOT NULL,
  first_observed VARCHAR(50) NOT NULL,
  last_observed VARCHAR(50) NOT NULL,
  imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS dataset_products (
  source_key VARCHAR(80) NOT NULL,
  record_id VARCHAR(100) NOT NULL,
  product_id VARCHAR(100) NOT NULL,
  title VARCHAR(700) NOT NULL,
  category VARCHAR(200) NOT NULL,
  category_path TEXT NOT NULL,
  brand VARCHAR(300) NOT NULL,
  price DECIMAL(14,2) NULL,
  mrp DECIMAL(14,2) NULL,
  rating DECIMAL(3,2) NULL,
  observed_at VARCHAR(50) NOT NULL,
  product_url TEXT NOT NULL,
  description MEDIUMTEXT NOT NULL,
  specifications MEDIUMTEXT NOT NULL,
  PRIMARY KEY (source_key, record_id),
  CONSTRAINT fk_dataset_source FOREIGN KEY (source_key) REFERENCES dataset_sources(source_key),
  CONSTRAINT chk_dataset_price CHECK (price IS NULL OR price >= 0),
  CONSTRAINT chk_dataset_mrp CHECK (mrp IS NULL OR mrp >= 0),
  CONSTRAINT chk_dataset_rating CHECK (rating IS NULL OR rating BETWEEN 0 AND 5),
  INDEX idx_dataset_category (category),
  INDEX idx_dataset_product_id (product_id)
);
