-- ============================================================
-- V2__add_weekly_tracking_tables.sql
-- 6S Score and Reprocess tracking tables
-- ============================================================

-- ─── 6S Score Record ─────────────────────────────────────────────────────────
CREATE TABLE sixs_record (
    id         BIGSERIAL   NOT NULL,
    data_month VARCHAR(7)  NOT NULL,
    section    VARCHAR(20) NOT NULL,
    line       VARCHAR(10) NOT NULL,
    week1      INT,
    week2      INT,
    week3      INT,
    week4      INT,
    week5      INT,
    CONSTRAINT pk_sixs_record          PRIMARY KEY (id),
    CONSTRAINT uq_sixs_month_sec_line  UNIQUE (data_month, section, line)
);

CREATE INDEX idx_sixs_data_month ON sixs_record (data_month);

-- ─── Reprocess Record ────────────────────────────────────────────────────────
CREATE TABLE reprocess_record (
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

CREATE INDEX idx_reprocess_data_month ON reprocess_record (data_month);
