ALTER TABLE t_users
    ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE t_email
(
    pk_email_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receiver    TEXT        NOT NULL REFERENCES t_users (email) ON DELETE CASCADE ON UPDATE CASCADE,
    subject     TEXT        NOT NULL,
    content     TEXT        NOT NULL,
    sending     BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
