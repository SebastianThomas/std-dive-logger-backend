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
         INNER JOIN t_dive_privileges p ON u.pk_user_id = p.fk_user_id;
