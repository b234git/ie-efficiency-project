-- ============================================================
-- V1__init_schema.sql
-- Initial schema for IE-Eff (Shoe Factory Efficiency Management)
-- Chạy trên: PostgreSQL 15+
-- Script này chỉ chạy trên DB mới hoàn toàn.
-- DB đang tồn tại (dev) sẽ được Flyway baseline và bỏ qua script này.
-- ============================================================

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id       BIGSERIAL     NOT NULL,
    username VARCHAR(50)   NOT NULL,
    password VARCHAR(255)  NOT NULL,
    role     VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- ─── Daily Production ────────────────────────────────────────────────────────
CREATE TABLE daily_production (
    id              BIGSERIAL        NOT NULL,
    production_date DATE             NOT NULL,
    section         VARCHAR(255)     NOT NULL,
    line            VARCHAR(255)     NOT NULL,
    mp              DOUBLE PRECISION NOT NULL,
    dli             DOUBLE PRECISION,
    idl             DOUBLE PRECISION,
    wt              DOUBLE PRECISION NOT NULL,
    rft             DOUBLE PRECISION,
    allowance       DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    total_output    INT              NOT NULL DEFAULT 0,
    user_id         BIGINT           NOT NULL,
    created_at      TIMESTAMP(6)     NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_daily_production PRIMARY KEY (id),
    CONSTRAINT fk_dp_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_dp_production_date ON daily_production (production_date);
CREATE INDEX idx_dp_user_id         ON daily_production (user_id);

-- ─── Daily Production Details ────────────────────────────────────────────────
CREATE TABLE daily_production_details (
    id                  BIGSERIAL    NOT NULL,
    daily_production_id BIGINT       NOT NULL,
    time_slot           VARCHAR(255) NOT NULL,
    article_no          VARCHAR(255) NOT NULL,
    output              INT          NOT NULL,
    CONSTRAINT pk_daily_production_details PRIMARY KEY (id),
    CONSTRAINT fk_dpd_production FOREIGN KEY (daily_production_id) REFERENCES daily_production(id)
);

-- ─── Master DB ───────────────────────────────────────────────────────────────
CREATE TABLE master_db (
    id                 BIGSERIAL        NOT NULL,
    ref                VARCHAR(255)     NOT NULL,
    data_month         VARCHAR(7),
    article_no         VARCHAR(255)     NOT NULL,
    pattern_no         VARCHAR(255),
    shoe_name          VARCHAR(255),
    os_code            VARCHAR(255),
    tct                DOUBLE PRECISION DEFAULT 0.0,
    -- SEW
    sew_ct             DOUBLE PRECISION,
    sew_mp             DOUBLE PRECISION,
    sew_quota_db       DOUBLE PRECISION,
    sew_pph            DOUBLE PRECISION,
    -- BUFFING 1ST
    buff1st_ct         DOUBLE PRECISION,
    buff1st_mp         DOUBLE PRECISION,
    buff1st_quota_db   DOUBLE PRECISION,
    buff1st_pph        DOUBLE PRECISION,
    -- BUFFING 2ND
    buff2nd_ct         DOUBLE PRECISION,
    buff2nd_mp         DOUBLE PRECISION,
    buff2nd_quota_db   DOUBLE PRECISION,
    buff2nd_pph        DOUBLE PRECISION,
    -- STOCKFIT UV
    stockfit_uv_ct       DOUBLE PRECISION,
    stockfit_uv_mp       DOUBLE PRECISION,
    stockfit_uv_quota_db DOUBLE PRECISION,
    stockfit_uv_pph      DOUBLE PRECISION,
    -- STOCKFIT 1ST
    stockfit1st_ct       DOUBLE PRECISION,
    stockfit1st_mp       DOUBLE PRECISION,
    stockfit1st_quota_db DOUBLE PRECISION,
    stockfit1st_pph      DOUBLE PRECISION,
    -- STOCKFIT 2ND
    stockfit2nd_ct       DOUBLE PRECISION,
    stockfit2nd_mp       DOUBLE PRECISION,
    stockfit2nd_quota_db DOUBLE PRECISION,
    stockfit2nd_pph      DOUBLE PRECISION,
    -- ASSEMBLY BIG
    assem_big_ct         DOUBLE PRECISION,
    assem_big_mp         DOUBLE PRECISION,
    assem_big_quota_db   DOUBLE PRECISION,
    assem_big_pph        DOUBLE PRECISION,
    -- ASSEMBLY SMALL
    assem_small_ct       DOUBLE PRECISION,
    assem_small_mp       DOUBLE PRECISION,
    assem_small_quota_db DOUBLE PRECISION,
    assem_small_pph      DOUBLE PRECISION,
    CONSTRAINT pk_master_db PRIMARY KEY (id),
    CONSTRAINT uq_master_db_ref_month UNIQUE (ref, data_month)
);

CREATE INDEX idx_mdb_article_no ON master_db (article_no);
CREATE INDEX idx_mdb_data_month ON master_db (data_month);

-- ─── Split Entry ─────────────────────────────────────────────────────────────
CREATE TABLE split_entry (
    id                         BIGSERIAL        NOT NULL,
    production_date            DATE             NOT NULL,
    section                    VARCHAR(255)     NOT NULL,
    line                       VARCHAR(255)     NOT NULL,
    mp                         DOUBLE PRECISION,
    dli                        DOUBLE PRECISION,
    idl                        DOUBLE PRECISION,
    wt                         DOUBLE PRECISION,
    total_output               INT,
    rft                        DOUBLE PRECISION,
    allowance                  DOUBLE PRECISION DEFAULT 1.0,
    manpower_filled_by         BIGINT,
    output_filled_by           BIGINT,
    article_filled_by          BIGINT,
    linked_daily_production_id BIGINT,
    status                     VARCHAR(255)     NOT NULL DEFAULT 'PARTIAL',
    created_at                 TIMESTAMP(6)     NOT NULL,
    updated_at                 TIMESTAMP(6),
    CONSTRAINT pk_split_entry PRIMARY KEY (id),
    CONSTRAINT uq_split_entry_date_section_line UNIQUE (production_date, section, line),
    CONSTRAINT fk_se_manpower_user  FOREIGN KEY (manpower_filled_by) REFERENCES users(id),
    CONSTRAINT fk_se_output_user    FOREIGN KEY (output_filled_by)   REFERENCES users(id),
    CONSTRAINT fk_se_article_user   FOREIGN KEY (article_filled_by)  REFERENCES users(id)
);

CREATE INDEX idx_se_production_date ON split_entry (production_date);
CREATE INDEX idx_se_status          ON split_entry (status);

-- ─── Split Entry Details ─────────────────────────────────────────────────────
CREATE TABLE split_entry_details (
    id             BIGSERIAL    NOT NULL,
    split_entry_id BIGINT       NOT NULL,
    time_slot      VARCHAR(255) NOT NULL,
    article_no     VARCHAR(255) NOT NULL,
    output         INT          NOT NULL,
    CONSTRAINT pk_split_entry_details PRIMARY KEY (id),
    CONSTRAINT fk_sed_split_entry FOREIGN KEY (split_entry_id) REFERENCES split_entry(id)
);

-- ─── Notifications ───────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id             BIGSERIAL    NOT NULL,
    recipient_role VARCHAR(255) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    message        TEXT,
    type           VARCHAR(255) NOT NULL,
    is_read        BOOLEAN      DEFAULT FALSE,
    created_at     TIMESTAMP(6) DEFAULT NOW(),
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX idx_notif_role_read  ON notifications (recipient_role, is_read);
CREATE INDEX idx_notif_created_at ON notifications (created_at);

-- ─── System Logs ─────────────────────────────────────────────────────────────
CREATE TABLE system_logs (
    id        BIGSERIAL    NOT NULL,
    username  VARCHAR(255) NOT NULL,
    action    VARCHAR(255) NOT NULL,
    details   TEXT,
    timestamp TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_system_logs PRIMARY KEY (id)
);

CREATE INDEX idx_syslog_timestamp ON system_logs (timestamp);
