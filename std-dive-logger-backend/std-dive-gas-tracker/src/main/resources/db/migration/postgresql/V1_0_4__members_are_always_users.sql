-- Nobody is invited by email any more (DOMAIN §2): a member is always a user. Waiting invitations
-- go away; one that already has fills or costs makes this DELETE fail, on purpose, rather than
-- losing money records.
DELETE FROM t_team_member WHERE fk_user_id IS NULL;
ALTER TABLE t_team_member DROP CONSTRAINT ck_team_member_user_or_email;
ALTER TABLE t_team_member DROP CONSTRAINT uq_team_member_team_invited_email;
DROP INDEX ix_team_member_invited_email;
ALTER TABLE t_team_member DROP COLUMN invited_email;
ALTER TABLE t_team_member ALTER COLUMN fk_user_id SET NOT NULL;
