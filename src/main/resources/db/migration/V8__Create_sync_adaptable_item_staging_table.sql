-- DATA-001 : table de STAGING pour l'import sync-adaptable.
-- Même schéma de colonnes que sync_adaptable_item (V4). Volontairement SANS les index de
-- lecture (idx_sync_adaptable_*) : la staging n'est lue qu'une seule fois, par un full-scan,
-- lors de la promotion atomique (INSERT ... SELECT ... FROM sync_adaptable_item_staging).
-- Des index ralentiraient inutilement les inserts en masse de l'import.
CREATE TABLE sync_adaptable_item_staging (
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
