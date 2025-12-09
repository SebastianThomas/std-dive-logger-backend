-- Fuzzy string matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- USERS
CREATE TABLE t_users
(
    pk_user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      TEXT        NOT NULL,
    password   TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    verified   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (email)
);
CREATE INDEX idx_user_name_trgm ON t_users USING GIN (name gin_trgm_ops);
-- AUTH
CREATE TABLE t_refresh_tokens
(
    jti        TEXT        NOT NULL PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
-- COMPUTER MANUFACTURERS
CREATE TABLE t_computer_manufacturer
(
    pk_manufacturer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               TEXT NOT NULL,
    UNIQUE (name)
);
-- Manufacturer name
CREATE INDEX idx_computer_manufacturer_name_trgm
    ON t_computer_manufacturer USING GIN (name gin_trgm_ops);
-- COMPUTERS
CREATE TABLE t_dive_computer
(
    pk_dive_computer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id          INTEGER REFERENCES t_users (pk_user_id),
    fk_manufacturer_id  INTEGER REFERENCES t_computer_manufacturer (pk_manufacturer_id),
    serial_number       TEXT,
    custom_identifier   TEXT NOT NULL,
    UNIQUE (fk_user_id, fk_manufacturer_id, serial_number),
    UNIQUE (fk_user_id, custom_identifier)
);
-- Computer model/custom_identifier
CREATE INDEX idx_dive_computer_custom_identifier_trgm
    ON t_dive_computer USING GIN (custom_identifier gin_trgm_ops);
-- GROUPS
CREATE TABLE t_groups
(
    pk_group_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_name  TEXT NOT NULL,
    UNIQUE (group_name)
);
CREATE INDEX idx_group_name_trgm ON t_groups USING GIN (group_name gin_trgm_ops);
-- DIVE SITE
CREATE TABLE t_dive_site
(
    pk_dive_site_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT                  NOT NULL,
    location        Geometry(Point, 4326) NOT NULL,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    UNIQUE (name)
);
CREATE INDEX idx_dive_site_name_trgm ON t_dive_site USING GIN (name gin_trgm_ops);
-- DIVES
CREATE TABLE t_dives
(
    pk_dive_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dive_number     INTEGER NOT NULL,
    dive_identifier TEXT,
    preview_image   TEXT,
    dive_site       INTEGER NOT NULL REFERENCES t_dive_site (pk_dive_site_id),
    fk_diver_id     INTEGER REFERENCES t_users (pk_user_id),
    UNIQUE (dive_number, fk_diver_id)
);
CREATE INDEX idx_dive_identifier_trgm ON t_dives USING GIN (dive_identifier gin_trgm_ops);
-- DIVE PROFILES
CREATE TABLE t_dive_profiles
(
    pk_dive_profile_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id         INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_dive_computer   INTEGER     NOT NULL REFERENCES t_dive_computer (pk_dive_computer_id),
    dive_profile_start TIMESTAMPTZ NOT NULL,
    dive_profile_end   TIMESTAMPTZ NOT NULL,
    UNIQUE (fk_dive_computer, dive_profile_start)
);
-- DIVE LENGTH
CREATE VIEW t_dive_length
AS
SELECT pk_dive_id, MIN(dive_profile_start) AS dive_start, MAX(dive_profile_end) as dive_end
FROM t_dives
         INNER JOIN t_dive_profiles ON pk_dive_id = fk_dive_id
GROUP BY pk_dive_id;
-- MEASUREMENTS
CREATE TABLE t_dive_measurements
(
    pk_dive_measurement_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_profile_id     INTEGER REFERENCES t_dive_profiles (pk_dive_profile_id),
    time                   TIMESTAMPTZ      NOT NULL,
    depth                  DOUBLE PRECISION NOT NULL,
    temperature_celsius    DOUBLE PRECISION NOT NULL,
    deco_stops             JSONB,
    ndl_minutes            INTEGER          NOT NULL
);
-- NAMED BUDDIES
CREATE TABLE t_dive_buddy_name
(
    pk_dive_buddy_name_id INTEGER GENERATED ALWAYS AS IDENTITY,
    fk_dive_id            INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    name                  TEXT    NOT NULL,
    UNIQUE (fk_dive_id, name)
);
-- REFERENCED BUDDIES
CREATE TABLE t_dive_buddy
(
    pk_dive_buddy_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id       INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_buddy_dive_id INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    CHECK (fk_dive_id < fk_buddy_dive_id)
);
-- GROUP MEMBERS
CREATE TABLE t_group_member
(
    pk_group_member INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_group_id     INTEGER REFERENCES t_groups (pk_group_id),
    fk_user_id      INTEGER REFERENCES t_users (pk_user_id),
    role            VARCHAR(10) NOT NULL
);
-- PRIVILEGES
CREATE TABLE t_dive_privileges
(
    pk_dive_privilege_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id           INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_user_id           INTEGER NOT NULL REFERENCES t_users (pk_user_id)
);
CREATE TABLE t_dive_privileges_groups
(
    pk_dive_privilege_group_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id                 INTEGER REFERENCES t_dives (pk_dive_id),
    fk_group_id                INTEGER REFERENCES t_groups (pk_group_id),
    UNIQUE (fk_dive_id, fk_group_id)
);

-- ANALYTICS
CREATE TABLE t_dive_analytics
(
    pk_dive_analytics_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id           INTEGER NOT NULL REFERENCES t_dives (pk_dive_id) UNIQUE
);

-- READERS
CREATE VIEW t_readers AS
-- Diver
SELECT d.pk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_dives d ON u.pk_user_id = d.fk_diver_id
UNION
-- Buddies (may include diver if >= 1 buddy
SELECT d.pk_dive_id AS dive_id, u.*
FROM t_dive_buddy b
         INNER JOIN t_dives d
                    ON b.fk_dive_id = d.pk_dive_id OR b.fk_buddy_dive_id = d.pk_dive_id
         INNER JOIN t_users u ON d.fk_diver_id = u.pk_user_id
UNION
-- Explicit Readers
SELECT p.fk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_dive_privileges p ON u.pk_user_id = p.fk_user_id
UNION
-- Group Readers
SELECT g.fk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_group_member m ON u.pk_user_id = m.fk_user_id AND role IN ('MEMBER', 'ADMIN')
         INNER JOIN t_dive_privileges_groups g ON g.fk_group_id = m.fk_group_id;

CREATE TABLE t_analytics_depth_variance
(
    fk_profile_id        INTEGER          NOT NULL REFERENCES t_dive_profiles (pk_dive_profile_id),
    fk_measurement_start INTEGER          NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id),
    fk_measurement_end   INTEGER          NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id),
    start_idx            INTEGER          NOT NULL,
    version              INTEGER          NOT NULL,
    avg_depth            DOUBLE PRECISION NOT NULL,
    max_depth            DOUBLE PRECISION NOT NULL,
    min_depth            DOUBLE PRECISION NOT NULL,
    deviation_avg        DOUBLE PRECISION NOT NULL,
    deviation_variance   DOUBLE PRECISION NOT NULL,
    deviation_01p        DOUBLE PRECISION NOT NULL,
    deviation_10p        DOUBLE PRECISION NOT NULL,
    deviation_median     DOUBLE PRECISION NOT NULL,
    deviation_90p        DOUBLE PRECISION NOT NULL,
    deviation_max        DOUBLE PRECISION GENERATED ALWAYS AS (GREATEST(ABS(max_depth - avg_depth), ABS(min_depth - avg_depth))) STORED,
    PRIMARY KEY (fk_profile_id, version, start_idx, fk_measurement_end)
);

CREATE OR REPLACE FUNCTION fuzzy_search_dives_for_user(
    search_term text,
    search_user_id int
)
    RETURNS TABLE
            (
                dive            t_dives,
                relevance_score float
            )
AS
$$
BEGIN
    RETURN QUERY
        WITH
            --
            readable_dives AS (
                --
                SELECT DISTINCT r.dive_id
                FROM t_readers r
                WHERE r.pk_user_id = search_user_id),
            --
            computer_scores AS (
                --
                SELECT dp.fk_dive_id,
                       GREATEST(
                               COALESCE(MAX(similarity(dc.custom_identifier, search_term)), 0),
                               COALESCE(MAX(similarity(cm.name, search_term) * 0.5), 0)
                       ) AS computer_score
                FROM t_dive_profiles dp
                         INNER JOIN readable_dives ad ON ad.dive_id = dp.fk_dive_id
                         LEFT JOIN t_dive_computer dc ON dc.pk_dive_computer_id = dp.fk_dive_computer
                         LEFT JOIN t_computer_manufacturer cm
                                   ON cm.pk_manufacturer_id = dc.fk_manufacturer_id
                GROUP BY dp.fk_dive_id)
        SELECT d,
               (
                   similarity(d.dive_identifier, search_term) * 5 + -- highest weight
                   similarity(ds.name, search_term) * 4 + -- high weight
                   similarity(u.name, search_term) * 2 + -- medium weight
                   COALESCE(cs.computer_score, 0) -- low weight
                   ) AS relevance_score
        FROM readable_dives ad
                 INNER JOIN t_dives d ON d.pk_dive_id = ad.dive_id
                 INNER JOIN t_dive_site ds ON ds.pk_dive_site_id = d.dive_site
                 LEFT JOIN t_users u ON u.pk_user_id = d.fk_diver_id
                 LEFT JOIN computer_scores cs ON cs.fk_dive_id = d.pk_dive_id
        WHERE d.dive_identifier % search_term
           OR ds.name % search_term
           OR u.name % search_term
           OR COALESCE(cs.computer_score, 0) > 0
        ORDER BY relevance_score DESC;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE t_email
(
    pk_email_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receiver    TEXT        NOT NULL REFERENCES t_users (email) ON DELETE CASCADE ON UPDATE CASCADE,
    subject     TEXT        NOT NULL,
    content     TEXT        NOT NULL,
    sending     BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE t_account_request
(
    pk_account_request_id TEXT                                     NOT NULL PRIMARY KEY,
    fk_user_id            INTEGER REFERENCES t_users (pk_user_id)  NOT NULL,
    fk_email_id           INTEGER REFERENCES t_email (pk_email_id) NOT NULL,
    request_type          TEXT                                     NOT NULL,
    valid_until           TIMESTAMPTZ                              NOT NULL,
    created_at            TIMESTAMPTZ                              NOT NULL
);
