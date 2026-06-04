-- ── Tag definitions ──────────────────────────────────────────────────────────
CREATE TABLE t_tag_definitions
(
    pk_tag_id        BIGSERIAL PRIMARY KEY,
    name             VARCHAR(128) NOT NULL,
    fk_user_id       INTEGER      REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    auto_detect_rule VARCHAR(64),
    -- A tag name must be unique per user (NULL user = system-wide)
    UNIQUE (fk_user_id, name),
    -- Only one tag definition per auto-detect rule (system-wide)
    UNIQUE (auto_detect_rule)
);

-- System-wide default tags (fk_user_id IS NULL = visible to all users)
INSERT INTO t_tag_definitions (name, fk_user_id, auto_detect_rule)
VALUES ('CCR',      NULL, 'CCR'),
       ('Deco',     NULL, 'DECO'),
       ('Cave',     NULL, NULL),
       ('Wreck',    NULL, NULL),
       ('Wall',     NULL, NULL),
       ('Night',    NULL, NULL),
       ('Deep',     NULL, NULL),
       ('Training', NULL, NULL);

-- ── Dive ↔ tag join table ─────────────────────────────────────────────────────
CREATE TABLE t_dive_tags
(
    pk_dive_tag_id BIGSERIAL PRIMARY KEY,
    fk_dive_id     BIGINT  NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    fk_tag_id      BIGINT  NOT NULL REFERENCES t_tag_definitions (pk_tag_id) ON DELETE CASCADE,
    manual         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (fk_dive_id, fk_tag_id)
);

-- GIN index so tag-name similarity lookups and the autocomplete LIKE are fast
CREATE INDEX idx_tag_def_name_trgm ON t_tag_definitions USING GIN (name gin_trgm_ops);

-- ── Full-text / fuzzy dive search ─────────────────────────────────────────────
--
-- Rewritten to use *additive* scoring across every searchable field so that
-- a query like "Mallorca CCR John" (partial site + tag + buddy) yields a high
-- combined score even though no individual field is a strong standalone match.
--
-- Strategy
-- --------
-- 1. Pre-compute per-dive scores for each searchable dimension in separate CTEs.
-- 2. The WHERE clause uses a very low per-field threshold (0.08) with OR so that
--    any weak signal in any field gets the dive into the candidate set.
-- 3. The final ORDER BY is on the *sum* of all dimension scores, so cross-field
--    partial matches accumulate and outrank a single strong field match.
--
-- Score weights (tunable):
--   dive identifier   ×5   (highest: user's own naming, most specific)
--   site name         ×4
--   tag names         ×3   (aggregate max across all applied tags)
--   buddy names       ×2.5 (aggregate max across named + linked-user buddies)
--   diver/owner name  ×2
--   computer          ×1   (lowest: usually not searched by name)
--
CREATE OR REPLACE FUNCTION fuzzy_search_dives_for_user(
    search_term    text,
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
            -- ── 1. Readable dives for this user ────────────────────────────
            readable_dives AS (
                SELECT DISTINCT r.dive_id
                FROM v_readers r
                WHERE r.pk_user_id = search_user_id
            ),
            -- ── 2. Computer score (max across all profiles of a dive) ──────
            computer_scores AS (
                SELECT dp.fk_dive_id,
                       GREATEST(
                           COALESCE(MAX(similarity(dc.custom_identifier, search_term)), 0),
                           COALESCE(MAX(similarity(cm.name,              search_term) * 0.5), 0)
                       ) AS score
                FROM t_dive_profiles dp
                         INNER JOIN readable_dives rd ON rd.dive_id = dp.fk_dive_id
                         LEFT JOIN t_dive_computer dc
                                   ON dc.pk_dive_computer_id = dp.fk_dive_computer
                         LEFT JOIN t_computer_manufacturer cm
                                   ON cm.pk_manufacturer_id = dc.fk_manufacturer_id
                GROUP BY dp.fk_dive_id
            ),
            -- ── 3. Tag score (max similarity across all tags on a dive) ────
            tag_scores AS (
                SELECT dt.fk_dive_id,
                       COALESCE(MAX(similarity(td.name, search_term)), 0) AS score
                FROM t_dive_tags dt
                         INNER JOIN readable_dives rd ON rd.dive_id = dt.fk_dive_id
                         INNER JOIN t_tag_definitions td ON td.pk_tag_id = dt.fk_tag_id
                GROUP BY dt.fk_dive_id
            ),
            -- ── 4. Named-buddy score (max similarity across free-text names) ─
            named_buddy_scores AS (
                SELECT bn.fk_dive_id,
                       COALESCE(MAX(similarity(bn.name, search_term)), 0) AS score
                FROM t_dive_buddy_name bn
                         INNER JOIN readable_dives rd ON rd.dive_id = bn.fk_dive_id
                GROUP BY bn.fk_dive_id
            ),
            -- ── 5. Linked-buddy user-name score ────────────────────────────
            linked_buddy_scores AS (
                SELECT DISTINCT ON (dive_id) dive_id,
                       COALESCE(MAX(similarity(u.name, search_term)), 0) AS score
                FROM (
                    -- dives where this dive is the "from" side
                    SELECT b.fk_dive_id AS dive_id, d2.fk_diver_id
                    FROM t_dive_buddy b
                             INNER JOIN readable_dives rd ON rd.dive_id = b.fk_dive_id
                             INNER JOIN t_dives d2 ON d2.pk_dive_id = b.fk_buddy_dive_id
                    UNION ALL
                    -- dives where this dive is the "to" side
                    SELECT b.fk_buddy_dive_id AS dive_id, d2.fk_diver_id
                    FROM t_dive_buddy b
                             INNER JOIN readable_dives rd ON rd.dive_id = b.fk_buddy_dive_id
                             INNER JOIN t_dives d2 ON d2.pk_dive_id = b.fk_dive_id
                ) buddy_dives
                         INNER JOIN t_users u ON u.pk_user_id = buddy_dives.fk_diver_id
                GROUP BY dive_id
            ),
            -- ── 6. Assemble per-dive totals ────────────────────────────────
            scored AS (
                SELECT
                    d.pk_dive_id,
                    d,
                    similarity(d.dive_identifier, search_term) * 5                       AS s_identifier,
                    similarity(ds.name,           search_term) * 4                       AS s_site,
                    COALESCE(ts.score,   0)                    * 3                       AS s_tag,
                    GREATEST(
                        COALESCE(nbs.score, 0),
                        COALESCE(lbs.score, 0)
                    )                                          * 2.5                     AS s_buddy,
                    similarity(u.name,            search_term) * 2                       AS s_owner,
                    COALESCE(cs.score,   0)        * 1                                   AS s_computer
                FROM readable_dives rd
                         INNER JOIN t_dives     d  ON d.pk_dive_id          = rd.dive_id
                         INNER JOIN t_dive_site ds ON ds.pk_dive_site_id    = d.dive_site
                         LEFT JOIN  t_users     u  ON u.pk_user_id          = d.fk_diver_id
                         LEFT JOIN  computer_scores  cs  ON cs.fk_dive_id   = d.pk_dive_id
                         LEFT JOIN  tag_scores       ts  ON ts.fk_dive_id   = d.pk_dive_id
                         LEFT JOIN  named_buddy_scores nbs ON nbs.fk_dive_id = d.pk_dive_id
                         LEFT JOIN  linked_buddy_scores lbs ON lbs.dive_id  = d.pk_dive_id
            )
        SELECT
            sc.d,
            (sc.s_identifier + sc.s_site + sc.s_tag + sc.s_buddy + sc.s_owner + sc.s_computer)
                AS relevance_score
        FROM scored sc
        -- Include a dive if *any* field clears a low individual threshold.
        -- This lets multi-field partial matches accumulate in the score above
        -- while still excluding completely unrelated dives.
        WHERE sc.s_identifier > 0.08
           OR sc.s_site       > 0.08
           OR sc.s_tag        > 0.08
           OR sc.s_buddy      > 0.08
           OR sc.s_owner      > 0.08
           OR sc.s_computer   > 0.08
        ORDER BY relevance_score DESC;
END;
$$ LANGUAGE plpgsql;
