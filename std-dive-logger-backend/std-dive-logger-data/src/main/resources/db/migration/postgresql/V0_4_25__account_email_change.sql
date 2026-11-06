-- Changing the account's email: the confirmation link goes to the NEW address (not a user yet) and
-- a notice to the OLD one (which `receiver` would follow via ON UPDATE CASCADE) - so an email can
-- name its recipient explicitly. A CHANGE_EMAIL request carries the address it switches to.
ALTER TABLE t_email ADD COLUMN send_to TEXT;
ALTER TABLE t_account_request ADD COLUMN new_email TEXT;

CREATE INDEX IF NOT EXISTS idx_account_request_user ON t_account_request (fk_user_id);
CREATE INDEX IF NOT EXISTS idx_account_request_email ON t_account_request (fk_email_id);
