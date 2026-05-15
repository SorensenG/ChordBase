CREATE TABLE chords (
    uuid uuid NOT NULL PRIMARY KEY,
    name varchar(255),
    chord_pro varchar(255),
    status varchar(255),
    source_type varchar(255),
    confidence double precision
);

CREATE TABLE user_chords (
    user_uuid uuid NOT NULL,
    chord_uuid uuid NOT NULL,
    CONSTRAINT user_chords_primary_key PRIMARY KEY (user_uuid, chord_uuid),
    CONSTRAINT fk_user_chords_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_chords_chord FOREIGN KEY (chord_uuid) REFERENCES chords(uuid) ON DELETE CASCADE
);

CREATE INDEX idx_user_chords_chord_uuid ON user_chords(chord_uuid);
