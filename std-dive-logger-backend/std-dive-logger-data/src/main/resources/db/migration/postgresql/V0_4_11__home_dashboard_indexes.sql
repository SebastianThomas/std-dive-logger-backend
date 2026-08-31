-- The only index on t_dives leads with dive_number (UNIQUE(dive_number, fk_diver_id)), so every
-- per-user query - the dive list, the stats aggregations, and now the home dashboard - filters
-- t_dives by fk_diver_id with no usable index and falls back to a sequential scan. One plain
-- b-tree makes those diver-scoped scans index-friendly; the home endpoint runs on nearly every
-- page load, so it matters there in particular.
CREATE INDEX IF NOT EXISTS idx_dives_diver_id ON t_dives (fk_diver_id);
