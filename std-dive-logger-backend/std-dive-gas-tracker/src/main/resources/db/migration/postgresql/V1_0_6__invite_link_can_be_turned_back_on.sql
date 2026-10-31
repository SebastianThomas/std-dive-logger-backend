-- Turning the invite link off keeps its token, so the same link can be turned on again
-- (DOMAIN §2). Without a token there is nothing to turn on.
ALTER TABLE t_team ADD COLUMN invite_link_on BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE t_team SET invite_link_on = TRUE WHERE invite_token IS NOT NULL;
ALTER TABLE t_team ADD CONSTRAINT ck_team_invite_link_on_has_token
    CHECK (NOT invite_link_on OR invite_token IS NOT NULL);
