CREATE TABLE setlists (
    uuid uuid NOT NULL PRIMARY KEY,
    name varchar(255) NOT NULL,
    description text,
    visibility varchar(255) NOT NULL,
    owner_user_uuid uuid NOT NULL,
    CONSTRAINT fk_setlists_owner_user FOREIGN KEY (owner_user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

CREATE TABLE setlist_chords (
    uuid uuid NOT NULL PRIMARY KEY,
    setlist_uuid uuid NOT NULL,
    chord_uuid uuid NOT NULL,
    chord_position integer NOT NULL,
    CONSTRAINT fk_setlist_chords_setlist FOREIGN KEY (setlist_uuid) REFERENCES setlists(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_setlist_chords_chord FOREIGN KEY (chord_uuid) REFERENCES chords(uuid) ON DELETE CASCADE,
    CONSTRAINT setlist_chords_setlist_chord_unique UNIQUE (setlist_uuid, chord_uuid)
);

CREATE TABLE setlist_collaborators (
    uuid uuid NOT NULL PRIMARY KEY,
    setlist_uuid uuid NOT NULL,
    user_uuid uuid NOT NULL,
    CONSTRAINT fk_setlist_collaborators_setlist FOREIGN KEY (setlist_uuid) REFERENCES setlists(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_setlist_collaborators_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT setlist_collaborators_setlist_user_unique UNIQUE (setlist_uuid, user_uuid)
);

CREATE INDEX idx_setlists_owner_user_uuid ON setlists(owner_user_uuid);
CREATE INDEX idx_setlist_chords_setlist_uuid ON setlist_chords(setlist_uuid);
CREATE INDEX idx_setlist_chords_chord_uuid ON setlist_chords(chord_uuid);
CREATE INDEX idx_setlist_collaborators_setlist_uuid ON setlist_collaborators(setlist_uuid);
CREATE INDEX idx_setlist_collaborators_user_uuid ON setlist_collaborators(user_uuid);
