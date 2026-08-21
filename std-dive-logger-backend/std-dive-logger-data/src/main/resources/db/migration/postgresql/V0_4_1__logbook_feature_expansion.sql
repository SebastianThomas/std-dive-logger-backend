-- Collapsed from what were originally 8 separate migrations (V0_4_1..V0_4_4, further re-collapsed
-- from an original V0_4_1..V0_4_5 pass) during development - none had been committed/applied
-- anywhere outside local dev yet, so they're merged into one here rather than kept as separate
-- steps. Covers: water type/current conditions, certification tracking, named- and linked-buddy
-- roles, dive-leader tracking, buddy/team terminology, a pre-existing v_readers view bug fix found
-- while testing buddy sharing, community-editable dive site metadata + stats, the dive photo
-- gallery, and dive trips/courses (with nesting).

-- ── Water type & current strength ───────────────────────────────────────────────────────────────
-- Mirrors t_dive_visibility's shape (a one-to-one side table keyed by dive id, created lazily on
-- first edit rather than at import time since no importer source currently supplies this data).
CREATE TABLE t_dive_conditions
(
    fk_dive_id          BIGINT PRIMARY KEY REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    water_type          VARCHAR(16),
    current_knots       DOUBLE PRECISION,
    current_description TEXT,
    current_feeling     INTEGER
);

-- ── Certification tracking ──────────────────────────────────────────────────────────────────────
-- Certifying agencies are a closed, shared lookup list (mirrors t_computer_manufacturer's role
-- for dive computers) rather than free text on each certification - this is deliberately *more*
-- friction than dive sites (which at least let a first-time site through with a location pick):
-- adding a new agency is a separate, explicitly-labelled action gated behind first searching the
-- existing list (see CertificationController's own doc comments), and duplicate names (case-
-- insensitive) are rejected outright rather than silently reused.
-- full_name/website_url/description are nullable so the system-seeded rows below (which are
-- trusted, curated data) don't have to fill them in, but CertificationController.createAgency
-- requires all three for anything a *user* adds - see CreateCertificationAgencyBody's own doc
-- comment for why (raising the bar against troll/duplicate entries).
CREATE TABLE t_certification_agency
(
    pk_agency_id BIGSERIAL PRIMARY KEY,
    name         TEXT NOT NULL UNIQUE,
    full_name    TEXT,
    website_url  TEXT,
    description  TEXT
);

-- Full names/URLs below are supplied to the best of available knowledge and worth a spot-check
-- against each agency's current official site before relying on them for anything beyond display.
INSERT INTO t_certification_agency (name, full_name, website_url)
VALUES ('PADI', 'Professional Association of Diving Instructors', 'https://www.padi.com'),
       ('SSI', 'Scuba Schools International', 'https://www.divessi.com'),
       ('NAUI', 'National Association of Underwater Instructors', 'https://www.naui.org'),
       ('CMAS', 'World Underwater Federation (Confédération Mondiale des Activités Subaquatiques)', 'https://www.cmas.org'),
       ('SDI', 'Scuba Diving International', 'https://www.tdisdi.com'),
       ('TDI', 'Technical Diving International', 'https://www.tdisdi.com'),
       ('RAID', 'Rebreather Association of International Divers', 'https://raiddivers.com'),
       ('BSAC', 'British Sub-Aqua Club', 'https://www.bsac.com'),
       ('GUE', 'Global Underwater Explorers', 'https://www.gue.com'),
       ('IANTD', 'International Association of Nitrox and Technical Divers', 'https://iantd.com'),
       ('ANDI', 'American Nitrox Divers International', 'https://andihq.com'),
       ('PSAI', 'Professional Scuba Association International', 'https://psai.org'),
       ('ACUC', 'ACUC International', 'https://www.acuc-international.com'),
       ('SNSI', 'Scuba Nitrox Safety International', 'https://www.snsi.education');

-- Diver certification tracking (agency, level, date, and optional links/ids) - strictly owned by
-- the diver themselves, no system/shared variant (unlike tag definitions).
CREATE TABLE t_certification
(
    pk_certification_id BIGSERIAL PRIMARY KEY,
    fk_user_id           INTEGER NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    fk_agency_id          BIGINT  NOT NULL REFERENCES t_certification_agency (pk_agency_id),
    level                 TEXT    NOT NULL,
    cert_date             DATE    NOT NULL,
    cert_id               TEXT,
    instructor_name       TEXT,
    facility              TEXT,
    course_link           TEXT,
    certification_link    TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_certification_user ON t_certification (fk_user_id);

-- ── Named-buddy roles, dive-leader tracking, buddy/team terminology ────────────────────────────
-- Named-buddy role: unambiguous since a named buddy only ever exists from the current dive
-- owner's perspective (no directionality issue, unlike the symmetric t_dive_buddy pair table).
ALTER TABLE t_dive_buddy_name
    ADD COLUMN role VARCHAR(32);

-- t_dive_buddy_name's identity column was never actually declared PRIMARY KEY (only
-- UNIQUE(fk_dive_id, name) existed) - needed so fk_leader_named_buddy_id below can reference it.
ALTER TABLE t_dive_buddy_name
    ADD CONSTRAINT t_dive_buddy_name_pkey PRIMARY KEY (pk_dive_buddy_name_id);

-- Dive-leader tracking: exactly one of these two may be set, or neither (meaning the dive's own
-- owner led). fk_leader_named_buddy_id points at a named buddy on THIS dive; fk_leader_buddy_dive_id
-- points at another dive whose owner is a linked buddy on this one ("that diver led").
ALTER TABLE t_dives
    ADD COLUMN fk_leader_named_buddy_id INTEGER REFERENCES t_dive_buddy_name (pk_dive_buddy_name_id) ON DELETE SET NULL,
    ADD COLUMN fk_leader_buddy_dive_id INTEGER REFERENCES t_dives (pk_dive_id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_dive_leader_at_most_one
        CHECK (fk_leader_named_buddy_id IS NULL OR fk_leader_buddy_dive_id IS NULL);

-- Buddy/team terminology override for display purposes only (see DiveLeader/TeamTerminology and
-- their frontend composable) - null falls back to "Buddy" (or a trip's own override, once trips
-- exist).
ALTER TABLE t_dives
    ADD COLUMN team_terminology VARCHAR(16);

-- ── Linked-buddy (t_dive_buddy) directional roles ───────────────────────────────────────────────
-- t_dive_buddy is symmetric in storage (fk_dive_id is always the lower id) but role is inherently
-- directional, so each side gets its own column - see DiveBuddyEntity.roleAsSeenFrom/
-- setRoleAsSeenFrom for how the two are resolved per viewpoint.
ALTER TABLE t_dive_buddy
    ADD COLUMN role_of_buddy_from_dive VARCHAR(32),
    ADD COLUMN role_of_dive_from_buddy VARCHAR(32);

-- ── v_readers view fix ───────────────────────────────────────────────────────────────────────────
-- v_readers was created in V0_2_5 with `SELECT ... u.*` - Postgres freezes a view's column list
-- at CREATE VIEW time, so the custom_icon_url/custom_background_url columns V0_3_1 later added to
-- t_users never actually appeared in this view. Any full-entity read through it (e.g.
-- UserRepository.findReaders(diveId), used by DiveService.addReaders/getReaders) then failed with
-- "column ... was not found in this ResultSet" once Hibernate tried to hydrate those fields -
-- isReader(diveId, userId) (a plain COUNT(*)) was unaffected and always worked, which is why this
-- went unnoticed. Dropping and recreating with the exact same query re-expands u.* to the current
-- column list.
DROP VIEW v_readers;

CREATE VIEW v_readers AS
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

-- ── Dive site metadata (WS6) ─────────────────────────────────────────────────────────────────────
-- Community-editable: any user who's logged >= 1 dive at a site may edit description/links/type/
-- maxDepth/countryRegion (see DiveDataService.hasLoggedDiveAtSite) - name/coordinates stay
-- create/dedupe-only, not editable here.
ALTER TABLE t_dive_site
    ADD COLUMN description    TEXT,
    ADD COLUMN country_region TEXT,
    ADD COLUMN max_depth      DOUBLE PRECISION,
    ADD COLUMN site_type      VARCHAR(32);

CREATE TABLE t_dive_site_link
(
    pk_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_site_id INTEGER NOT NULL REFERENCES t_dive_site (pk_dive_site_id) ON DELETE CASCADE,
    url        TEXT    NOT NULL,
    label      TEXT
);

CREATE INDEX idx_dive_site_link_site ON t_dive_site_link (fk_site_id);

-- ── Dive photo gallery (WS4) ─────────────────────────────────────────────────────────────────────
-- Uploads go through a presigned-URL flow (see StorageService's presignedUploadUrl/download
-- additions): the frontend requests an upload URL, which creates a row here in an unconfirmed
-- (confirmed = false) state up front to avoid orphaned storage objects if the direct PUT to
-- storage never completes, then confirms once the PUT succeeds. byte_size is therefore nullable -
-- it's only known once the frontend reports it back at confirm time.
CREATE TABLE t_dive_photo
(
    pk_photo_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id             INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    storage_path           TEXT        NOT NULL,
    content_type           TEXT        NOT NULL,
    byte_size              BIGINT,
    fk_uploaded_by_user_id INTEGER     NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    caption                TEXT,
    taken_at               TIMESTAMPTZ,
    confirmed              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dive_photo_dive ON t_dive_photo (fk_dive_id);

-- ── Dive trips / training groups, with nesting (WS7) ────────────────────────────────────────────
CREATE TABLE t_dive_trip
(
    pk_trip_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             TEXT        NOT NULL,
    type             VARCHAR(16) NOT NULL,
    fk_owner_user_id INTEGER     NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    team_terminology VARCHAR(16),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dive_trip_owner ON t_dive_trip (fk_owner_user_id);

-- A trip's members are either individual dives or other trips (for nesting, e.g. "2026 Season"
-- containing a "Greece Holiday" trip and a "Deco Procedures" course) - exactly one of the two
-- member columns is set per row.
CREATE TABLE t_dive_trip_member
(
    pk_id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_trip_id        INTEGER NOT NULL REFERENCES t_dive_trip (pk_trip_id) ON DELETE CASCADE,
    fk_member_dive_id INTEGER REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    fk_member_trip_id INTEGER REFERENCES t_dive_trip (pk_trip_id) ON DELETE CASCADE,
    CONSTRAINT chk_dive_trip_member_exactly_one CHECK (
        (fk_member_dive_id IS NOT NULL AND fk_member_trip_id IS NULL) OR
        (fk_member_dive_id IS NULL AND fk_member_trip_id IS NOT NULL)
        ),
    CONSTRAINT chk_dive_trip_member_no_self_reference CHECK (fk_trip_id <> fk_member_trip_id)
);

CREATE INDEX idx_dive_trip_member_trip ON t_dive_trip_member (fk_trip_id);
CREATE UNIQUE INDEX idx_dive_trip_member_dive_unique ON t_dive_trip_member (fk_trip_id, fk_member_dive_id)
    WHERE fk_member_dive_id IS NOT NULL;
CREATE UNIQUE INDEX idx_dive_trip_member_trip_unique ON t_dive_trip_member (fk_trip_id, fk_member_trip_id)
    WHERE fk_member_trip_id IS NOT NULL;
-- Fast lookup of "which trip(s) is this dive part of" (e.g. a trip badge on the dive view).
CREATE INDEX idx_dive_trip_member_dive ON t_dive_trip_member (fk_member_dive_id) WHERE fk_member_dive_id IS NOT NULL;

-- A trip's default team roster - a prefill template only, copied onto a dive's named buddies when
-- the dive is added to the trip, then edited normally per-dive from there.
CREATE TABLE t_dive_trip_default_team
(
    pk_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_trip_id       INTEGER     NOT NULL REFERENCES t_dive_trip (pk_trip_id) ON DELETE CASCADE,
    fk_buddy_user_id INTEGER REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    buddy_name       TEXT,
    role             VARCHAR(32) NOT NULL,
    CONSTRAINT chk_dive_trip_default_team_exactly_one CHECK (
        (fk_buddy_user_id IS NOT NULL AND buddy_name IS NULL) OR
        (fk_buddy_user_id IS NULL AND buddy_name IS NOT NULL)
        )
);

CREATE INDEX idx_dive_trip_default_team_trip ON t_dive_trip_default_team (fk_trip_id);
