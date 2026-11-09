-- Each task belongs to the user who created it, and only that user can see or change it.
-- Nullable on purpose: tasks created before Stage 4 have no owner. They stay in the table but no user
-- can see them through the API until they are given an owner. A later migration makes the column
-- NOT NULL once no ownerless tasks remain.
ALTER TABLE tasks ADD COLUMN owner_id BIGINT REFERENCES users (id);
