-- Track auto-detected tags that the user has explicitly dismissed.
-- dismissed = TRUE means the tag was auto-detectable but the user removed it;
-- it will not be re-added automatically until the user adds it back manually.
ALTER TABLE t_dive_tags
    ADD COLUMN dismissed BOOLEAN NOT NULL DEFAULT FALSE;
