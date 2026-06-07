-- Objects are created in the schema configured via spring.flyway.default-schema
-- (`character`), which the tm_<env>_character role owns. No hard-coded schema name.
CREATE TABLE pc (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    clazz       TEXT,
    level       SMALLINT,
    player_name TEXT,
    user_id     UUID
);
