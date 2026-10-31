-- What a team tracks (DOMAIN §2), and shared CO2 absorbent canisters, kept apart from gas (§12).
ALTER TABLE t_team
    ADD COLUMN gas_tracking_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN scrubber_tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Emptied <=> emptied_at is set. Who may change what: DOMAIN §12.
CREATE TABLE t_scrubber_canister
(
    pk_scrubber_canister_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_team_id              BIGINT           NOT NULL REFERENCES t_team (pk_team_id),
    label                   VARCHAR(60)      NOT NULL CHECK (length(btrim(label)) > 0),
    capacity_kg             DOUBLE PRECISION NOT NULL CHECK (capacity_kg > 0 AND capacity_kg <= 1000),
    opened_at               TIMESTAMPTZ      NOT NULL,
    emptied_at              TIMESTAMPTZ,
    fk_created_by_member_id BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_scrubber_canister_emptied_after_opened CHECK (emptied_at IS NULL OR emptied_at > opened_at)
);
CREATE INDEX ix_scrubber_canister_team ON t_scrubber_canister (fk_team_id);
CREATE INDEX ix_scrubber_canister_created_by ON t_scrubber_canister (fk_created_by_member_id);

CREATE TABLE t_scrubber_cost_line
(
    pk_scrubber_cost_line_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_scrubber_canister_id  BIGINT         NOT NULL REFERENCES t_scrubber_canister (pk_scrubber_canister_id),
    fk_payer_member_id       BIGINT         NOT NULL REFERENCES t_team_member (pk_team_member_id),
    amount                   NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount < 1000000000),
    paid_at                  TIMESTAMPTZ    NOT NULL,
    note                     VARCHAR(200),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_scrubber_cost_line_canister ON t_scrubber_cost_line (fk_scrubber_canister_id);
CREATE INDEX ix_scrubber_cost_line_payer ON t_scrubber_cost_line (fk_payer_member_id);

-- One scoop into a rebreather: an estimate in kg, never weighed exactly.
CREATE TABLE t_scrubber_use
(
    pk_scrubber_use_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_scrubber_canister_id  BIGINT           NOT NULL REFERENCES t_scrubber_canister (pk_scrubber_canister_id),
    fk_member_id             BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    kg                       DOUBLE PRECISION NOT NULL CHECK (kg > 0 AND kg <= 1000),
    used_at                  TIMESTAMPTZ      NOT NULL,
    fk_recorded_by_member_id BIGINT           NOT NULL REFERENCES t_team_member (pk_team_member_id),
    created_at               TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX ix_scrubber_use_canister ON t_scrubber_use (fk_scrubber_canister_id);
CREATE INDEX ix_scrubber_use_member ON t_scrubber_use (fk_member_id);
CREATE INDEX ix_scrubber_use_recorded_by ON t_scrubber_use (fk_recorded_by_member_id);
