-- Extensions the schema relies on. Both ship with PostgreSQL's contrib modules.
--
-- btree_gist lets a GiST index/exclusion constraint compare plain equality columns (a seat id)
--   together with range overlap (a time range). V4 uses it so two bookings for the same seat
--   cannot overlap in time, enforced by the database itself.
-- pg_trgm provides trigram indexes so "contains" searches on book titles and authors are indexed.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
