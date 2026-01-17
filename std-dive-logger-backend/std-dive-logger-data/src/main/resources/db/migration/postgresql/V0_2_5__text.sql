DROP VIEW t_readers;

ALTER TABLE t_suits
    ALTER COLUMN type TYPE TEXT;

ALTER TABLE t_dive_configuration
    ALTER COLUMN base_configuration TYPE TEXT;

ALTER TABLE t_dive_profile_segments
    ALTER COLUMN type TYPE TEXT;

ALTER TABLE t_group_member
    ALTER COLUMN role TYPE TEXT;

-- READERS
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
                FROM v_readers r
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
