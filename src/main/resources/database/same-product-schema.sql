-- Run after multistore-schema.sql. Does not manufacture or modify product prices.
USE tulita;
CREATE TABLE IF NOT EXISTS canonical_variants (
 group_key VARCHAR(300) PRIMARY KEY,
 canonical_id VARCHAR(80) NOT NULL,
 canonical_name VARCHAR(500) NOT NULL,
 brand VARCHAR(200) NOT NULL,
 manufacturer_model VARCHAR(200) NOT NULL,
 category VARCHAR(200) NOT NULL,
 variant_key VARCHAR(160) NOT NULL,
 size_value VARCHAR(150) NOT NULL,
 color_value VARCHAR(150) NOT NULL,
 capacity_value VARCHAR(150) NOT NULL,
 quantity_value VARCHAR(150) NOT NULL,
 gender_value VARCHAR(150) NOT NULL
);
CREATE TABLE IF NOT EXISTS same_product_links (
 group_key VARCHAR(300) NOT NULL,
 store VARCHAR(20) NOT NULL,
 record_id VARCHAR(150) NOT NULL UNIQUE,
 record_sha256 CHAR(64) NOT NULL,
 evidence_url TEXT NOT NULL,
 reviewed_on DATE NOT NULL,
 review_note TEXT NOT NULL,
 PRIMARY KEY(group_key,store),
 CONSTRAINT fk_review_group FOREIGN KEY(group_key) REFERENCES canonical_variants(group_key) ON DELETE CASCADE,
 CONSTRAINT fk_review_source FOREIGN KEY(record_id) REFERENCES multi_records(record_id) ON DELETE CASCADE,
 CONSTRAINT chk_review_store CHECK(store IN ('Amazon','Flipkart','Myntra'))
);
-- Replacing source records invalidates their old links through ON DELETE CASCADE.
-- Re-review/re-import mappings after changing/replacing a source catalog.
