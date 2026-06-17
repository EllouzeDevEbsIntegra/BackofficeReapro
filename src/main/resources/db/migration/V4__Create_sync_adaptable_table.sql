CREATE TABLE sync_adaptable_item (
    id BIGSERIAL PRIMARY KEY,
    ext_id BIGINT,
    td_ref VARCHAR(255),
    td_brand_id INT,
    td_brand_name VARCHAR(255),
    td_description VARCHAR(500),
    oem VARCHAR(255),
    description VARCHAR(500),
    master VARCHAR(255),
    part_make_code VARCHAR(255),
    part_group_code VARCHAR(255),
    part_group_name VARCHAR(255),
    part_subgroup_code VARCHAR(255),
    part_subgroup_name VARCHAR(255),
    champs_libre VARCHAR(255)
);

CREATE INDEX idx_sync_adaptable_brand ON sync_adaptable_item(td_brand_name);
CREATE INDEX idx_sync_adaptable_group ON sync_adaptable_item(part_group_code);
CREATE INDEX idx_sync_adaptable_subgroup ON sync_adaptable_item(part_subgroup_code);
CREATE INDEX idx_sync_adaptable_master ON sync_adaptable_item(master);
