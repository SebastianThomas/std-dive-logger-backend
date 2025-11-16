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

CREATE TABLE t_computer_manufacturer
(
    pk_manufacturer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               TEXT NOT NULL,
    UNIQUE (name)
);

CREATE TABLE t_dive_computer
(
    pk_dive_computer_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id          INTEGER REFERENCES t_users (pk_user_id),
    fk_manufacturer_id  INTEGER REFERENCES t_computer_manufacturer (pk_manufacturer_id),
    serial_number       TEXT,
    custom_identifier   TEXT NOT NULL
);

CREATE TABLE t_groups
(
    pk_group_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_name  TEXT NOT NULL,
    UNIQUE (group_name)
);

CREATE TABLE t_dive_site
(
    pk_dive_site_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT                  NOT NULL,
    location        Geometry(Point, 4326) NOT NULL,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    UNIQUE (name)
);

CREATE TABLE t_dives
(
    pk_dive_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dive_number     INTEGER NOT NULL,
    dive_identifier TEXT,
    dive_site       INTEGER NOT NULL REFERENCES t_dive_site (pk_dive_site_id),
    fk_diver_id     INTEGER REFERENCES t_users (pk_user_id),
    UNIQUE (dive_number, fk_diver_id)
);

CREATE TABLE t_dive_profiles
(
    pk_dive_profile_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id         INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_dive_computer   INTEGER     NOT NULL REFERENCES t_dive_computer (pk_dive_computer_id),
    dive_profile_start TIMESTAMPTZ NOT NULL,
    dive_profile_end   TIMESTAMPTZ NOT NULL
);

CREATE VIEW t_dive_length
AS
SELECT pk_dive_id, MIN(dive_profile_start) AS dive_start, MAX(dive_profile_end) as dive_end
FROM t_dives
         INNER JOIN t_dive_profiles ON pk_dive_id = fk_dive_id
GROUP BY pk_dive_id;

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

CREATE TABLE t_dive_privileges
(
    pk_dive_privilege_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id           INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_user_id           INTEGER NOT NULL REFERENCES t_users (pk_user_id)
);

CREATE TABLE t_dive_buddy
(
    pk_dive_buddy_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id       INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    fk_buddy_dive_id INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    CHECK (fk_dive_id < fk_buddy_dive_id)
);

CREATE TABLE t_group_member
(
    pk_group_member INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_group_id     INTEGER REFERENCES t_groups (pk_group_id),
    fk_user_id      INTEGER REFERENCES t_users (pk_user_id)
);

-- ANALYTICS
CREATE TABLE t_dive_analytics
(
    pk_dive_analytics_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id           INTEGER NOT NULL REFERENCES t_dives (pk_dive_id)
);