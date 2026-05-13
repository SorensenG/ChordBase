CREATE TABLE refresh_tokens (
    uuid uuid NOT NULL PRIMARY KEY,
    user_uuid uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_uuid ON refresh_tokens(user_uuid);
