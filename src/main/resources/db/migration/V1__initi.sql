-- V1__init.sql

CREATE TABLE roles (
                       uuid uuid NOT NULL PRIMARY KEY,
                       role varchar(255)
);

CREATE TABLE users (
                       uuid uuid NOT NULL PRIMARY KEY,
                       user_name varchar(255) NOT NULL,
                       email varchar(255) NOT NULL,
                       password_hash varchar(255) NOT NULL,
                       CONSTRAINT users_email_unique UNIQUE (email),
                       CONSTRAINT users_username_unique UNIQUE (user_name)
);

CREATE TABLE user_roles (
                            user_uuid uuid NOT NULL,
                            role_uuid uuid NOT NULL,
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_uuid) REFERENCES roles(uuid) ON DELETE CASCADE
);
