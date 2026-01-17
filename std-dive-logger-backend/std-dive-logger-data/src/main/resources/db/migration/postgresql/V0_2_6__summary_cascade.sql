ALTER TABLE t_dive_summary
    DROP CONSTRAINT t_dive_summary_fk_dive_id_fkey,
    ADD CONSTRAINT t_dive_summary_fk_dive_id_fkey
        FOREIGN KEY ("fk_dive_id")
            REFERENCES t_dives ("pk_dive_id")
            ON DELETE CASCADE;
