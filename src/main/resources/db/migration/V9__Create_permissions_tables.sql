-- =====================================================================
-- Lot 1 RBAC — Base backend des permissions Reapro
-- Crée le registre central des permissions et les affectations utilisateurs.
--
-- ⚠️ Flyway n'est PAS actif dans ce projet (cf. PostgresDataSourceConfig :
--    hibernate.hbm2ddl.auto = "validate" + évolution de schéma par SQL/DDL manuel).
--    => Exécuter CE script manuellement sur la base PostgreSQL `ReaproAchat`
--       AVANT de démarrer l'application, sinon la validation Hibernate des entités
--       Permission / UserPermission échouera au démarrage.
--
-- Idempotent (CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS).
-- =====================================================================

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    label       VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    group_name  VARCHAR(80),
    type        VARCHAR(20)  NOT NULL,
    sensitive   BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_permissions (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_by    BIGINT,
    granted_at    TIMESTAMP,
    CONSTRAINT uq_user_permission UNIQUE (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user
        FOREIGN KEY (user_id)       REFERENCES admins (id)      ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_permissions_user       ON user_permissions (user_id);
CREATE INDEX IF NOT EXISTS idx_user_permissions_permission ON user_permissions (permission_id);
