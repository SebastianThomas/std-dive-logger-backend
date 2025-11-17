-- Fuzzy string matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- USERS
CREATE TABLE t_users
(
    pk_user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      TEXT        NOT NULL,
    password   TEXT        NOT NULL,
    name       TEXT        NOT NULL,
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
-- COMPUTERS
CREATE TABLE t_dive_computer
(
    pk_dive_computer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id          INTEGER REFERENCES t_users (pk_user_id),
    fk_manufacturer_id  INTEGER REFERENCES t_computer_manufacturer (pk_manufacturer_id),
    serial_number       TEXT,
    custom_identifier   TEXT NOT NULL,
    UNIQUE (fk_manufacturer_id, serial_number),
    UNIQUE (fk_user_id, custom_identifier)
);
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
    fk_user_id      INTEGER REFERENCES t_users (pk_user_id)
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
         INNER JOIN t_group_member m ON u.pk_user_id = m.fk_user_id
         INNER JOIN t_dive_privileges_groups g ON g.fk_group_id = m.fk_group_id;
