-- ============================================================
-- IE-Eff — Full Database Init Script (PostgreSQL 15+)
-- Tổng hợp từ V1→V7 + các bảng không có trong Flyway
--
-- Cách dùng:
--   psql -U ie_app -h localhost -d shoe_eff_db -f init_db.sql
--
-- Sau khi chạy xong, khởi động app bình thường.
-- Flyway sẽ tự baseline ở V7 và không chạy lại migrations.
-- ============================================================

-- ─── Users ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id       BIGSERIAL    NOT NULL,
    username VARCHAR(50)  NOT NULL,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- ─── Daily Production ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_production (
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
    version         BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_daily_production PRIMARY KEY (id),
    CONSTRAINT fk_dp_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_dp_production_date   ON daily_production (production_date);
CREATE INDEX IF NOT EXISTS idx_dp_user_id           ON daily_production (user_id);
CREATE INDEX IF NOT EXISTS idx_dp_section_line      ON daily_production (section, line);
CREATE INDEX IF NOT EXISTS idx_dp_date_section_line ON daily_production (production_date, section, line);

-- ─── Daily Production Details ────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_production_details (
    id                  BIGSERIAL    NOT NULL,
    daily_production_id BIGINT       NOT NULL,
    time_slot           VARCHAR(255) NOT NULL,
    article_no          VARCHAR(255) NOT NULL,
    output              INT          NOT NULL,
    CONSTRAINT pk_daily_production_details PRIMARY KEY (id),
    CONSTRAINT fk_dpd_production FOREIGN KEY (daily_production_id) REFERENCES daily_production(id)
);

-- ─── Master DB ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS master_db (
    id                   BIGSERIAL        NOT NULL,
    ref                  VARCHAR(255)     NOT NULL,
    data_month           VARCHAR(7),
    article_no           VARCHAR(255)     NOT NULL,
    pattern_no           VARCHAR(255),
    shoe_name            VARCHAR(255),
    os_code              VARCHAR(255),
    tct                  DOUBLE PRECISION DEFAULT 0.0,
    sew_ct               DOUBLE PRECISION,
    sew_mp               DOUBLE PRECISION,
    sew_quota_db         DOUBLE PRECISION,
    sew_pph              DOUBLE PRECISION,
    buff1st_ct           DOUBLE PRECISION,
    buff1st_mp           DOUBLE PRECISION,
    buff1st_quota_db     DOUBLE PRECISION,
    buff1st_pph          DOUBLE PRECISION,
    buff2nd_ct           DOUBLE PRECISION,
    buff2nd_mp           DOUBLE PRECISION,
    buff2nd_quota_db     DOUBLE PRECISION,
    buff2nd_pph          DOUBLE PRECISION,
    stockfit_uv_ct       DOUBLE PRECISION,
    stockfit_uv_mp       DOUBLE PRECISION,
    stockfit_uv_quota_db DOUBLE PRECISION,
    stockfit_uv_pph      DOUBLE PRECISION,
    stockfit1st_ct       DOUBLE PRECISION,
    stockfit1st_mp       DOUBLE PRECISION,
    stockfit1st_quota_db DOUBLE PRECISION,
    stockfit1st_pph      DOUBLE PRECISION,
    stockfit2nd_ct       DOUBLE PRECISION,
    stockfit2nd_mp       DOUBLE PRECISION,
    stockfit2nd_quota_db DOUBLE PRECISION,
    stockfit2nd_pph      DOUBLE PRECISION,
    assem_big_ct         DOUBLE PRECISION,
    assem_big_mp         DOUBLE PRECISION,
    assem_big_quota_db   DOUBLE PRECISION,
    assem_big_pph        DOUBLE PRECISION,
    assem_small_ct       DOUBLE PRECISION,
    assem_small_mp       DOUBLE PRECISION,
    assem_small_quota_db DOUBLE PRECISION,
    assem_small_pph      DOUBLE PRECISION,
    CONSTRAINT pk_master_db PRIMARY KEY (id),
    CONSTRAINT uq_master_db_ref_month UNIQUE (ref, data_month)
);

CREATE INDEX IF NOT EXISTS idx_mdb_article_no ON master_db (article_no);
CREATE INDEX IF NOT EXISTS idx_mdb_data_month ON master_db (data_month);
CREATE INDEX IF NOT EXISTS idx_mdb_ref        ON master_db (ref);

-- ─── Split Entry ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS split_entry (
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
    version                    BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_split_entry PRIMARY KEY (id),
    CONSTRAINT uq_split_entry_date_section_line UNIQUE (production_date, section, line),
    CONSTRAINT fk_se_manpower_user FOREIGN KEY (manpower_filled_by) REFERENCES users(id),
    CONSTRAINT fk_se_output_user   FOREIGN KEY (output_filled_by)   REFERENCES users(id),
    CONSTRAINT fk_se_article_user  FOREIGN KEY (article_filled_by)  REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_se_production_date ON split_entry (production_date);
CREATE INDEX IF NOT EXISTS idx_se_status          ON split_entry (status);
CREATE INDEX IF NOT EXISTS idx_se_section_line    ON split_entry (section, line);

-- ─── Split Entry Details ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS split_entry_details (
    id             BIGSERIAL    NOT NULL,
    split_entry_id BIGINT       NOT NULL,
    time_slot      VARCHAR(255) NOT NULL,
    article_no     VARCHAR(255) NOT NULL,
    output         INT          NOT NULL,
    CONSTRAINT pk_split_entry_details PRIMARY KEY (id),
    CONSTRAINT fk_sed_split_entry FOREIGN KEY (split_entry_id) REFERENCES split_entry(id)
);

-- ─── Notifications ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGSERIAL    NOT NULL,
    recipient_role VARCHAR(255) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    message        TEXT,
    type           VARCHAR(255) NOT NULL,
    is_read        BOOLEAN      DEFAULT FALSE,
    created_at     TIMESTAMP(6) DEFAULT NOW(),
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_notif_role_read  ON notifications (recipient_role, is_read);
CREATE INDEX IF NOT EXISTS idx_notif_created_at ON notifications (created_at);

-- ─── System Logs ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_logs (
    id         BIGSERIAL    NOT NULL,
    username   VARCHAR(255) NOT NULL,
    action     VARCHAR(255) NOT NULL,
    details    TEXT,
    timestamp  TIMESTAMP(6) NOT NULL,
    ip_address VARCHAR(45),
    CONSTRAINT pk_system_logs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_syslog_timestamp ON system_logs (timestamp);

-- ─── 6S Score Record ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sixs_record (
    id         BIGSERIAL   NOT NULL,
    data_month VARCHAR(7)  NOT NULL,
    section    VARCHAR(20) NOT NULL,
    line       VARCHAR(10) NOT NULL,
    week1      INT,
    week2      INT,
    week3      INT,
    week4      INT,
    week5      INT,
    CONSTRAINT pk_sixs_record         PRIMARY KEY (id),
    CONSTRAINT uq_sixs_month_sec_line UNIQUE (data_month, section, line)
);

CREATE INDEX IF NOT EXISTS idx_sixs_data_month ON sixs_record (data_month);

-- ─── Reprocess Record ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reprocess_record (
    id         BIGSERIAL   NOT NULL,
    data_month VARCHAR(7)  NOT NULL,
    section    VARCHAR(20) NOT NULL,
    line       VARCHAR(10) NOT NULL,
    week1      INT,
    week2      INT,
    week3      INT,
    week4      INT,
    week5      INT,
    output     INT,
    CONSTRAINT pk_reprocess_record         PRIMARY KEY (id),
    CONSTRAINT uq_reprocess_month_sec_line UNIQUE (data_month, section, line)
);

CREATE INDEX IF NOT EXISTS idx_reprocess_data_month ON reprocess_record (data_month);

-- ─── New Style Entry ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS new_style_entry (
    id         BIGSERIAL   NOT NULL,
    data_month VARCHAR(7)  NOT NULL,
    section    VARCHAR(20) NOT NULL,
    line       VARCHAR(10) NOT NULL,
    style      VARCHAR(50) NOT NULL,
    quantity   INT         NOT NULL,
    CONSTRAINT pk_new_style_entry PRIMARY KEY (id)
);

-- ─── Import Jobs ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS import_jobs (
    id            BIGSERIAL    NOT NULL,
    job_type      VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    progress      INT          NOT NULL DEFAULT 0,
    total_rows    INT          NOT NULL DEFAULT 0,
    error_message TEXT,
    created_by    VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_import_jobs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_import_jobs_created_by ON import_jobs (created_by, created_at DESC);

-- ─── Eff Multiplier ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS eff_multiplier (
    id         BIGSERIAL        NOT NULL,
    sec        VARCHAR(10)      NOT NULL,
    section    VARCHAR(10)      NOT NULL,
    wt         INT              NOT NULL,
    rate_alias VARCHAR(10),
    grade1     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    grade2     DOUBLE PRECISION,
    grade3     DOUBLE PRECISION,
    grade4     DOUBLE PRECISION,
    grade5     DOUBLE PRECISION,
    grade6     DOUBLE PRECISION,
    grade7     DOUBLE PRECISION,
    grade8     DOUBLE PRECISION,
    grade9     DOUBLE PRECISION,
    CONSTRAINT pk_eff_multiplier  PRIMARY KEY (id),
    CONSTRAINT uq_eff_multiplier_sec UNIQUE (sec)
);

-- ─── Eff Incentive Rate ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS eff_incentive_rate (
    id          BIGSERIAL        NOT NULL,
    sec         VARCHAR(10)      NOT NULL,
    section     VARCHAR(10)      NOT NULL,
    wt          INT              NOT NULL,
    eff_percent DOUBLE PRECISION NOT NULL,
    rate        DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_eff_incentive_rate         PRIMARY KEY (id),
    CONSTRAINT uq_eff_incentive_rate_sec_pct UNIQUE (sec, eff_percent)
);
