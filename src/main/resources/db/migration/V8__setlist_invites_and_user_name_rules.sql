UPDATE users
SET user_name = lower(user_name);

CREATE UNIQUE INDEX users_user_name_lower_unique
    ON users (lower(user_name));

ALTER TABLE setlist_collaborators
    ADD COLUMN status varchar(255) NOT NULL DEFAULT 'PENDING';

ALTER TABLE setlist_collaborators
    ADD COLUMN invited_by_user_uuid uuid;

ALTER TABLE setlist_collaborators
    ADD CONSTRAINT fk_setlist_collaborators_invited_by_user
    FOREIGN KEY (invited_by_user_uuid) REFERENCES users(uuid);
