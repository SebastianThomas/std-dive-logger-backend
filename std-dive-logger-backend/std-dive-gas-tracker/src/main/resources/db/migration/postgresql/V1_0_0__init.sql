-- Gas tracker schema (own database, not the dive logger's).
-- Until the first deployment (task P1) this is the ONLY migration and is edited in place; reset the
-- local DB after editing it: `docker compose down -v` in the module directory. See AGENTS.md
-- "Migrations". Range and length checks mirror the request records (model.vessel.Limits etc.).

-- User: local copy of a dive-logger user (DOMAIN §2). The id IS the dive-logger user id (JWT `uid`,
-- positive; dev-login uses negative ids), never generated here. email is the cached, current
-- login address: NULL after another user claimed it (stale), set again at that user's next request.
CREATE TABLE t_user
(
    pk_user_id   BIGINT PRIMARY KEY,
    email        VARCHAR(320) UNIQUE CHECK (email = lower(email) AND position('@' IN email) > 1),
    display_name VARCHAR(60)  NOT NULL CHECK (length(display_name) > 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Range checks are DOMAIN §3. Statuses are derived from nullable timestamps, never stored.

-- Team: tenant and accounting unit, incl. its settings (DOMAIN §2).
CREATE TABLE t_team
(
    pk_team_id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                          VARCHAR(60)      NOT NULL CHECK (length(btrim(name)) > 0),
    currency                      CHAR(3)          NOT NULL DEFAULT 'CHF' CHECK (currency ~ '^[A-Z]{3}$'),
    default_loss_policy           VARCHAR(10)      NOT NULL CHECK (default_loss_policy IN ('TRUE_UP', 'POOLED')),
    line_dead_volume_litres       DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (line_dead_volume_litres >= 0),
    default_residual_pressure_bar DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (default_residual_pressure_bar BETWEEN 0 AND 400),
    created_at                    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Member: a person's membership in one team (DOMAIN §2). PENDING <=> invited_email set (no user yet,
-- bound to fk_user_id at that email's first verified sign-in); LEFT <=> left_at is set.
CREATE TABLE t_team_member
(
    pk_team_member_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_team_id        BIGINT      NOT NULL REFERENCES t_team (pk_team_id),
    fk_user_id        BIGINT REFERENCES t_user (pk_user_id),
    invited_email     VARCHAR(320) CHECK (invited_email = lower(invited_email) AND position('@' IN invited_email) > 1),
    role              VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at           TIMESTAMPTZ,
    CONSTRAINT ck_team_member_user_or_email CHECK ((fk_user_id IS NULL) <> (invited_email IS NULL)),
    -- Also the index for fk_team_id (both constraints).
    CONSTRAINT uq_team_member_team_user UNIQUE (fk_team_id, fk_user_id),
    CONSTRAINT uq_team_member_team_invited_email UNIQUE (fk_team_id, invited_email)
);
CREATE INDEX ix_team_member_user ON t_team_member (fk_user_id);
-- Binding at sign-in looks pending rows up by email alone.
CREATE INDEX ix_team_member_invited_email ON t_team_member (invited_email) WHERE invited_email IS NOT NULL;

-- Vessel: a bank or house-mix container; CLOSED <=> closed_at (with closing_pressure_bar) is set.
CREATE TABLE t_vessel
(
    pk_vessel_id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_team_id                     BIGINT           NOT NULL REFERENCES t_team (pk_team_id),
    label                          VARCHAR(60)      NOT NULL CHECK (length(btrim(label)) > 0),
    water_volume_litres            DOUBLE PRECISION NOT NULL CHECK (water_volume_litres > 0 AND water_volume_litres <= 5000),
    mix_o2                         DOUBLE PRECISION NOT NULL CHECK (mix_o2 BETWEEN 0 AND 1),
    mix_he                         DOUBLE PRECISION NOT NULL CHECK (mix_he BETWEEN 0 AND 1),
    -- What the vessel held at initial_pressure_bar when registered (DOMAIN §6 step 1). The declared
    -- mix above may change later (an analysis after blending), this never does once gas was blended in.
    initial_o2                     DOUBLE PRECISION NOT NULL CHECK (initial_o2 BETWEEN 0 AND 1),
    initial_he                     DOUBLE PRECISION NOT NULL CHECK (initial_he BETWEEN 0 AND 1),
    initial_pressure_bar           DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (initial_pressure_bar BETWEEN 0 AND 400),
    expected_residual_pressure_bar DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (expected_residual_pressure_bar BETWEEN 0 AND 400),
    loss_policy                    VARCHAR(10)      NOT NULL CHECK (loss_policy IN ('TRUE_UP', 'POOLED')),
    opened_at                      TIMESTAMPTZ      NOT NULL,
    closed_at                      TIMESTAMPTZ,
    closing_pressure_bar           DOUBLE PRECISION CHECK (closing_pressure_bar BETWEEN 0 AND 400),
    notes                          VARCHAR(1000),
    fk_created_by_member_id        BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    created_at                     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_vessel_mix_sum CHECK (mix_o2 + mix_he <= 1 + 1e-9),
    CONSTRAINT ck_vessel_initial_mix_sum CHECK (initial_o2 + initial_he <= 1 + 1e-9),
    CONSTRAINT ck_vessel_closed CHECK ((closed_at IS NULL) = (closing_pressure_bar IS NULL)),
    -- The service also keeps every fill and reading of the vessel before closed_at (DOMAIN §6).
    CONSTRAINT ck_vessel_closed_after_opened CHECK (closed_at IS NULL OR closed_at > opened_at)
);
CREATE INDEX ix_vessel_team ON t_vessel (fk_team_id);
CREATE INDEX ix_vessel_created_by ON t_vessel (fk_created_by_member_id);

-- Money paid for a vessel; the payer is credited (DOMAIN §2, §8).
CREATE TABLE t_vessel_cost_line
(
    pk_vessel_cost_line_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_vessel_id           BIGINT         NOT NULL REFERENCES t_vessel (pk_vessel_id),
    fk_payer_member_id     BIGINT         NOT NULL REFERENCES t_team_member (pk_team_member_id),
    amount                 NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount < 1000000000),
    paid_at                TIMESTAMPTZ    NOT NULL,
    note                   VARCHAR(200),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_vessel_cost_line_vessel ON t_vessel_cost_line (fk_vessel_id);
CREATE INDEX ix_vessel_cost_line_payer ON t_vessel_cost_line (fk_payer_member_id);

-- Snapshot of the members sharing a closed vessel's loss (DOMAIN §7.3); empty while IN_SERVICE.
CREATE TABLE t_vessel_loss_participant
(
    fk_vessel_id BIGINT NOT NULL REFERENCES t_vessel (pk_vessel_id),
    fk_member_id BIGINT NOT NULL REFERENCES t_team_member (pk_team_member_id),
    -- Also the index for fk_vessel_id.
    PRIMARY KEY (fk_vessel_id, fk_member_id)
);
CREATE INDEX ix_vessel_loss_participant_member ON t_vessel_loss_participant (fk_member_id);

-- Fill: one filling operation; VOIDED <=> voided_at (with void_reason) is set; no money/warnings (DOMAIN §5.4).
CREATE TABLE t_fill
(
    pk_fill_id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_team_id               BIGINT           NOT NULL REFERENCES t_team (pk_team_id),
    filled_at                TIMESTAMPTZ      NOT NULL,
    fk_recorded_by_member_id BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    -- Target: a vessel, else a cylinder (water volume + charged member), see ck_fill_target.
    fk_target_vessel_id      BIGINT REFERENCES t_vessel (pk_vessel_id),
    water_volume_litres      DOUBLE PRECISION CHECK (water_volume_litres > 0 AND water_volume_litres <= 5000),
    cylinder_label           VARCHAR(60),
    fk_charged_member_id     BIGINT REFERENCES t_team_member (pk_team_member_id),
    start_pressure_bar       DOUBLE PRECISION NOT NULL CHECK (start_pressure_bar BETWEEN 0 AND 400),
    start_o2                 DOUBLE PRECISION NOT NULL CHECK (start_o2 BETWEEN 0 AND 1),
    start_he                 DOUBLE PRECISION NOT NULL CHECK (start_he BETWEEN 0 AND 1),
    end_pressure_bar         DOUBLE PRECISION NOT NULL CHECK (end_pressure_bar BETWEEN 0 AND 400),
    end_o2                   DOUBLE PRECISION NOT NULL CHECK (end_o2 BETWEEN 0 AND 1),
    end_he                   DOUBLE PRECISION NOT NULL CHECK (end_he BETWEEN 0 AND 1),
    end_analysis_o2          DOUBLE PRECISION CHECK (end_analysis_o2 BETWEEN 0 AND 1),
    end_analysis_he          DOUBLE PRECISION CHECK (end_analysis_he BETWEEN 0 AND 1),
    notes                    VARCHAR(1000),
    voided_at                TIMESTAMPTZ,
    void_reason              VARCHAR(200) CHECK (length(btrim(void_reason)) > 0),
    calc_model_version       VARCHAR(40)      NOT NULL,
    created_at               TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_fill_target CHECK (
        (fk_target_vessel_id IS NULL
            AND water_volume_litres IS NOT NULL AND fk_charged_member_id IS NOT NULL)
        OR (fk_target_vessel_id IS NOT NULL
            AND water_volume_litres IS NULL AND cylinder_label IS NULL AND fk_charged_member_id IS NULL)),
    CONSTRAINT ck_fill_start_mix_sum CHECK (start_o2 + start_he <= 1 + 1e-9),
    CONSTRAINT ck_fill_end_mix_sum CHECK (end_o2 + end_he <= 1 + 1e-9),
    -- Both analyser fractions or neither; a NULL sum passes the second term.
    CONSTRAINT ck_fill_end_analysis CHECK (
        (end_analysis_o2 IS NULL) = (end_analysis_he IS NULL)
        AND end_analysis_o2 + end_analysis_he <= 1 + 1e-9),
    CONSTRAINT ck_fill_void CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);
-- Fills list, newest first (also the index for fk_team_id).
CREATE INDEX ix_fill_team_filled_at ON t_fill (fk_team_id, filled_at DESC);
CREATE INDEX ix_fill_recorded_by ON t_fill (fk_recorded_by_member_id);
CREATE INDEX ix_fill_target_vessel ON t_fill (fk_target_vessel_id);
CREATE INDEX ix_fill_charged_member ON t_fill (fk_charged_member_id);

-- Fill stage: one source in order; fk_source_vessel_id NULL <=> AIR; source_o2/he snapshot its mix.
CREATE TABLE t_fill_stage
(
    pk_fill_stage_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_fill_id          BIGINT           NOT NULL REFERENCES t_fill (pk_fill_id),
    stage_index         INTEGER          NOT NULL CHECK (stage_index >= 0),
    fk_source_vessel_id BIGINT REFERENCES t_vessel (pk_vessel_id),
    source_o2           DOUBLE PRECISION NOT NULL CHECK (source_o2 BETWEEN 0 AND 1),
    source_he           DOUBLE PRECISION NOT NULL CHECK (source_he BETWEEN 0 AND 1),
    pressure_after_bar  DOUBLE PRECISION NOT NULL CHECK (pressure_after_bar BETWEEN 0 AND 400),
    added_litres        DOUBLE PRECISION NOT NULL CHECK (added_litres >= 0),
    added_o2_litres     DOUBLE PRECISION NOT NULL CHECK (added_o2_litres >= 0),
    added_he_litres     DOUBLE PRECISION NOT NULL CHECK (added_he_litres >= 0),
    line_loss_litres    DOUBLE PRECISION NOT NULL CHECK (line_loss_litres >= 0),
    draw_litres         DOUBLE PRECISION NOT NULL CHECK (draw_litres >= 0),
    -- The source bank's gauge after the stage as entered (also kept as a t_vessel_reading), so an
    -- edit can send it back.
    source_pressure_after_bar DOUBLE PRECISION CHECK (source_pressure_after_bar BETWEEN 0 AND 400),
    CONSTRAINT ck_fill_stage_source_mix_sum CHECK (source_o2 + source_he <= 1 + 1e-9),
    -- AIR (no source vessel) has no draw, no line loss and no bank gauge.
    CONSTRAINT ck_fill_stage_source CHECK (
        fk_source_vessel_id IS NOT NULL OR (line_loss_litres = 0 AND draw_litres = 0)),
    CONSTRAINT ck_fill_stage_gauge_bank_only CHECK (
        source_pressure_after_bar IS NULL OR fk_source_vessel_id IS NOT NULL),
    -- Also the index for fk_fill_id.
    CONSTRAINT uq_fill_stage_fill_index UNIQUE (fk_fill_id, stage_index)
);
CREATE INDEX ix_fill_stage_source_vessel ON t_fill_stage (fk_source_vessel_id);

-- Reading: measured vessel pressure (DOMAIN §6); fk_fill_id set if taken from a fill's stage (after t_fill).
CREATE TABLE t_vessel_reading
(
    pk_vessel_reading_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_vessel_id            BIGINT           NOT NULL REFERENCES t_vessel (pk_vessel_id),
    pressure_bar            DOUBLE PRECISION NOT NULL CHECK (pressure_bar BETWEEN 0 AND 400),
    measured_at             TIMESTAMPTZ      NOT NULL,
    fk_fill_id              BIGINT REFERENCES t_fill (pk_fill_id),
    fk_created_by_member_id BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX ix_vessel_reading_vessel ON t_vessel_reading (fk_vessel_id);
CREATE INDEX ix_vessel_reading_fill ON t_vessel_reading (fk_fill_id);
CREATE INDEX ix_vessel_reading_created_by ON t_vessel_reading (fk_created_by_member_id);

-- Money from one member to another to settle balances; counts as soon as recorded (DOMAIN §8).
CREATE TABLE t_payment
(
    pk_payment_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_team_id              BIGINT         NOT NULL REFERENCES t_team (pk_team_id),
    fk_from_member_id       BIGINT         NOT NULL REFERENCES t_team_member (pk_team_member_id),
    fk_to_member_id         BIGINT         NOT NULL REFERENCES t_team_member (pk_team_member_id),
    amount                  NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount < 1000000000),
    paid_at                 TIMESTAMPTZ    NOT NULL,
    note                    VARCHAR(200),
    fk_created_by_member_id BIGINT         NOT NULL REFERENCES t_team_member (pk_team_member_id),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_from_to CHECK (fk_from_member_id <> fk_to_member_id)
);
CREATE INDEX ix_payment_team ON t_payment (fk_team_id);
CREATE INDEX ix_payment_from ON t_payment (fk_from_member_id);
CREATE INDEX ix_payment_to ON t_payment (fk_to_member_id);
CREATE INDEX ix_payment_created_by ON t_payment (fk_created_by_member_id);
