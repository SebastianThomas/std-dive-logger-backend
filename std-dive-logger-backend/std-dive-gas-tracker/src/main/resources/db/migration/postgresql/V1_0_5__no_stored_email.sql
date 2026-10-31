-- The dive logger owns the email (DOMAIN §2): the gas tracker no longer keeps a copy.
ALTER TABLE t_user DROP COLUMN email;
