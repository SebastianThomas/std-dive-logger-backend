DELETE
FROM t_group_member;
ALTER TABLE t_group_member
    ADD COLUMN role VARCHAR(7);
