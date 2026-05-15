ALTER TABLE chords
    ADD COLUMN owner_user_uuid uuid;

ALTER TABLE chords
    ADD CONSTRAINT fk_chords_owner_user
    FOREIGN KEY (owner_user_uuid) REFERENCES users(uuid);
