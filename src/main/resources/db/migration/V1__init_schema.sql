-- ============================================================
-- V1__init_schema.sql
-- Initial schema for IE-Eff (Shoe Factory Efficiency Management)
-- Chạy trên: SQL Server 2019+
-- Script này chỉ chạy trên DB mới hoàn toàn.
-- DB đang tồn tại (dev) sẽ được Flyway baseline và bỏ qua script này.
-- ============================================================

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id       BIGINT IDENTITY(1,1) NOT NULL,
    username NVARCHAR(50)  NOT NULL,
    password NVARCHAR(255) NOT NULL,
    role     NVARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- ─── Daily Production ────────────────────────────────────────────────────────
CREATE TABLE daily_production (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    production_date DATE          NOT NULL,
    section         NVARCHAR(255) NOT NULL,
    line            NVARCHAR(255) NOT NULL,
    mp              FLOAT(53)     NOT NULL,
    dli             FLOAT(53),
    idl             FLOAT(53),
    wt              FLOAT(53)     NOT NULL,
    rft             FLOAT(53),
    allowance       FLOAT(53)     NOT NULL DEFAULT 1.0,
    total_output    INT           NOT NULL DEFAULT 0,
    user_id         BIGINT        NOT NULL,
    created_at      DATETIME2(6)  NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_daily_production PRIMARY KEY (id),
    CONSTRAINT fk_dp_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_dp_production_date ON daily_production (production_date);
CREATE INDEX idx_dp_user_id         ON daily_production (user_id);

-- ─── Daily Production Details ────────────────────────────────────────────────
CREATE TABLE daily_production_details (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    daily_production_id BIGINT        NOT NULL,
    time_slot           NVARCHAR(255) NOT NULL,
    article_no          NVARCHAR(255) NOT NULL,
    output              INT           NOT NULL,
    CONSTRAINT pk_daily_production_details PRIMARY KEY (id),
    CONSTRAINT fk_dpd_production FOREIGN KEY (daily_production_id) REFERENCES daily_production(id)
);

-- ─── Master DB ───────────────────────────────────────────────────────────────
CREATE TABLE master_db (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    ref                NVARCHAR(255) NOT NULL,
    data_month         NVARCHAR(7),
    article_no         NVARCHAR(255) NOT NULL,
    pattern_no         NVARCHAR(255),
    shoe_name          NVARCHAR(255),
    os_code            NVARCHAR(255),
    tct                FLOAT(53)     DEFAULT 0.0,
    -- SEW
    sew_ct             FLOAT(53),
    sew_mp             FLOAT(53),
    sew_quota_db       FLOAT(53),
    sew_pph            FLOAT(53),
    -- BUFFING 1ST
    buff1st_ct         FLOAT(53),
    buff1st_mp         FLOAT(53),
    buff1st_quota_db   FLOAT(53),
    buff1st_pph        FLOAT(53),
    -- BUFFING 2ND
    buff2nd_ct         FLOAT(53),
    buff2nd_mp         FLOAT(53),
    buff2nd_quota_db   FLOAT(53),
    buff2nd_pph        FLOAT(53),
    -- STOCKFIT UV
    stockfit_uv_ct       FLOAT(53),
    stockfit_uv_mp       FLOAT(53),
    stockfit_uv_quota_db FLOAT(53),
    stockfit_uv_pph      FLOAT(53),
    -- STOCKFIT 1ST
    stockfit1st_ct       FLOAT(53),
    stockfit1st_mp       FLOAT(53),
    stockfit1st_quota_db FLOAT(53),
    stockfit1st_pph      FLOAT(53),
    -- STOCKFIT 2ND
    stockfit2nd_ct       FLOAT(53),
    stockfit2nd_mp       FLOAT(53),
    stockfit2nd_quota_db FLOAT(53),
    stockfit2nd_pph      FLOAT(53),
    -- ASSEMBLY BIG
    assem_big_ct         FLOAT(53),
    assem_big_mp         FLOAT(53),
    assem_big_quota_db   FLOAT(53),
    assem_big_pph        FLOAT(53),
    -- ASSEMBLY SMALL
    assem_small_ct       FLOAT(53),
    assem_small_mp       FLOAT(53),
    assem_small_quota_db FLOAT(53),
    assem_small_pph      FLOAT(53),
    CONSTRAINT pk_master_db PRIMARY KEY (id),
    CONSTRAINT uq_master_db_ref_month UNIQUE (ref, data_month)
);

CREATE INDEX idx_mdb_article_no ON master_db (article_no);
CREATE INDEX idx_mdb_data_month ON master_db (data_month);

-- ─── Split Entry ─────────────────────────────────────────────────────────────
CREATE TABLE split_entry (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    production_date           DATE          NOT NULL,
    section                   NVARCHAR(255) NOT NULL,
    line                      NVARCHAR(255) NOT NULL,
    mp                        FLOAT(53),
    dli                       FLOAT(53),
    idl                       FLOAT(53),
    wt                        FLOAT(53),
    total_output              INT,
    rft                       FLOAT(53),
    allowance                 FLOAT(53)     DEFAULT 1.0,
    manpower_filled_by        BIGINT,
    output_filled_by          BIGINT,
    article_filled_by         BIGINT,
    linked_daily_production_id BIGINT,
    status                    NVARCHAR(255) NOT NULL DEFAULT 'PARTIAL',
    created_at                DATETIME2(6)  NOT NULL,
    updated_at                DATETIME2(6),
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
    id             BIGINT IDENTITY(1,1) NOT NULL,
    split_entry_id BIGINT        NOT NULL,
    time_slot      NVARCHAR(255) NOT NULL,
    article_no     NVARCHAR(255) NOT NULL,
    output         INT           NOT NULL,
    CONSTRAINT pk_split_entry_details PRIMARY KEY (id),
    CONSTRAINT fk_sed_split_entry FOREIGN KEY (split_entry_id) REFERENCES split_entry(id)
);

-- ─── Notifications ───────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    recipient_role NVARCHAR(255) NOT NULL,
    title          NVARCHAR(255) NOT NULL,
    message        NVARCHAR(MAX),
    type           NVARCHAR(255) NOT NULL,
    is_read        BIT           DEFAULT 0,
    created_at     DATETIME2(6)  DEFAULT GETDATE(),
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX idx_notif_role_read  ON notifications (recipient_role, is_read);
CREATE INDEX idx_notif_created_at ON notifications (created_at);

-- ─── System Logs ─────────────────────────────────────────────────────────────
CREATE TABLE system_logs (
    id        BIGINT IDENTITY(1,1) NOT NULL,
    username  NVARCHAR(255) NOT NULL,
    action    NVARCHAR(255) NOT NULL,
    details   NVARCHAR(MAX),
    timestamp DATETIME2(6)  NOT NULL,
    CONSTRAINT pk_system_logs PRIMARY KEY (id)
);

CREATE INDEX idx_syslog_timestamp ON system_logs (timestamp);
