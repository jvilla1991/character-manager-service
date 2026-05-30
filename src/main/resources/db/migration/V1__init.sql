CREATE SCHEMA IF NOT EXISTS character_manage;

CREATE TABLE character_manage.pc (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    clazz       TEXT,
    level       SMALLINT,
    player_name TEXT,
    user_id     UUID
);
