CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_dive_site_name_trgm ON t_dive_site USING GIN (name gin_trgm_ops);
CREATE INDEX idx_user_name_trgm ON t_users USING GIN (name gin_trgm_ops);
CREATE INDEX idx_group_name_trgm ON t_groups USING GIN (group_name gin_trgm_ops);
