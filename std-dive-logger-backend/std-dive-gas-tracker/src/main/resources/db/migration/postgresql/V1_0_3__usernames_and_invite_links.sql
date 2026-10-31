-- The dive logger's username, copied from the token like the email (DOMAIN §2); NULL until that
-- user's first request with a token carrying it. Unique ignoring case, as the dive logger checks.
ALTER TABLE t_user ADD COLUMN username VARCHAR(255) CHECK (length(btrim(username)) > 0);
CREATE UNIQUE INDEX uq_user_username ON t_user (lower(username));

-- The team's current invite link (DOMAIN §2); NULL: none. Whoever holds it can join.
ALTER TABLE t_team ADD COLUMN invite_token VARCHAR(64) UNIQUE;
