-- V6__Create_partslink_cache_tables.sql

CREATE TABLE partslink_vehicles (
    id BIGSERIAL PRIMARY KEY,
    vin VARCHAR(17) UNIQUE NOT NULL,
    model_designation VARCHAR(255),
    production_date VARCHAR(50),
    color VARCHAR(150),
    upholstery VARCHAR(255),
    transmission VARCHAR(100),
    model_code VARCHAR(100),
    model VARCHAR(150),
    brand_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE partslink_groups (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES partslink_vehicles(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vehicle_group_code UNIQUE (vehicle_id, code)
);

CREATE TABLE partslink_subgroups (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES partslink_groups(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_group_subgroup_code UNIQUE (group_id, code)
);

CREATE TABLE partslink_parts (
    id BIGSERIAL PRIMARY KEY,
    subgroup_id BIGINT NOT NULL REFERENCES partslink_subgroups(id) ON DELETE CASCADE,
    position VARCHAR(50),
    part_number VARCHAR(100),
    designation VARCHAR(500),
    info_suppl TEXT,
    quantity VARCHAR(50),
    ae VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE partslink_schematics (
    id BIGSERIAL PRIMARY KEY,
    subgroup_id BIGINT NOT NULL UNIQUE REFERENCES partslink_subgroups(id) ON DELETE CASCADE,
    image_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index pour optimiser les performances
CREATE INDEX idx_partslink_vehicles_vin ON partslink_vehicles(vin);
CREATE INDEX idx_partslink_groups_vehicle ON partslink_groups(vehicle_id);
CREATE INDEX idx_partslink_subgroups_group ON partslink_subgroups(group_id);
CREATE INDEX idx_partslink_parts_subgroup ON partslink_parts(subgroup_id);
