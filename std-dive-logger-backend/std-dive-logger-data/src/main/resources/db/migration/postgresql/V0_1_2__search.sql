-- Computer model/custom_identifier
CREATE INDEX idx_dive_computer_custom_identifier_trgm
    ON t_dive_computer USING GIN (custom_identifier gin_trgm_ops);

-- Manufacturer name
CREATE INDEX idx_computer_manufacturer_name_trgm
    ON t_computer_manufacturer USING GIN (name gin_trgm_ops);

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
