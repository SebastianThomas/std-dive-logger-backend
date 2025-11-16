CREATE TABLE t_dive_buddy_name
(
    pk_dive_buddy_name_id INTEGER GENERATED ALWAYS AS IDENTITY,
    fk_dive_id            INTEGER NOT NULL REFERENCES t_dives (pk_dive_id),
    name                  TEXT    NOT NULL
);
