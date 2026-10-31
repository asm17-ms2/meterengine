CREATE TABLE scheduled_job_run (
  id              UUID NOT NULL,
  job_name        VARCHAR NOT NULL,
  scheduled_at   TIMESTAMPTZ NOT NULL,
  status          VARCHAR NOT NULL
                    CONSTRAINT scheduled_job_run_status_check
                    CHECK (status IN ('running', 'succeeded', 'failed')),
  processed_count INTEGER NOT NULL DEFAULT 0,
  skipped_count   INTEGER NOT NULL DEFAULT 0,
  failed_count    INTEGER NOT NULL DEFAULT 0,
  error_summary   VARCHAR,
  started_at      TIMESTAMPTZ NOT NULL,
  finished_at     TIMESTAMPTZ,

  CONSTRAINT scheduled_job_run_pk PRIMARY KEY (id),

  CONSTRAINT scheduled_job_run_job_name_scheduled_at_unique
    UNIQUE (job_name, scheduled_at),

  CONSTRAINT scheduled_job_run_status_finished_at_check
    CHECK ((status = 'running') = (finished_at IS NULL))
);

CREATE INDEX scheduled_job_run_job_name_started_at
  ON scheduled_job_run (job_name, started_at DESC);
