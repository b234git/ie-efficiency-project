CREATE TABLE import_jobs (
    id          BIGSERIAL PRIMARY KEY,
    job_type    VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    progress    INT          NOT NULL DEFAULT 0,
    total_rows  INT          NOT NULL DEFAULT 0,
    error_message TEXT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_jobs_created_by ON import_jobs (created_by, created_at DESC);
