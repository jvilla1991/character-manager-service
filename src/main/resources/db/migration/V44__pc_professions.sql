CREATE TABLE pc_profession (
    id BIGSERIAL PRIMARY KEY,
    pc_id BIGINT NOT NULL REFERENCES pc(id) ON DELETE CASCADE,
    profession VARCHAR(20) NOT NULL CHECK (profession IN ('archaeology', 'fishing', 'herbalism', 'mining', 'skinning')),
    UNIQUE (pc_id, profession)
);
