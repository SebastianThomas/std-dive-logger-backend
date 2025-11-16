CREATE TABLE t_refresh_tokens
(
    jti        TEXT        NOT NULL PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);