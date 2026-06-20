-- Session Mode: a live encounter / initiative tracker layered on a campaign.
-- A DM opens one session per campaign; players join with a PC; the session
-- becomes a shared initiative order with a round/turn pointer.
--
-- Consistency rule: a PC's combat state (HP, conditions) stays canonical on the
-- pc row. A participant row carries only session-scoped data (ordering, turn,
-- death saves) plus stats for ad-hoc NPCs (pc_id NULL — wired up in a later
-- phase). There is never a divergent copy of a PC's HP.
CREATE TABLE IF NOT EXISTS combat_session (
    id                  BIGSERIAL PRIMARY KEY,
    campaign_id         BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    dm_user_id          UUID NOT NULL,
    status              TEXT NOT NULL DEFAULT 'LOBBY',   -- LOBBY | ACTIVE | ENDED
    round               SMALLINT NOT NULL DEFAULT 1,
    current_turn_index  SMALLINT NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,       -- bumped per mutation; drives poll diffing
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_combat_session_campaign_id ON combat_session (campaign_id);

-- At most one non-ended (LOBBY/ACTIVE) session per campaign; ENDED rows are
-- unconstrained so history accumulates.
CREATE UNIQUE INDEX IF NOT EXISTS ux_combat_session_active_per_campaign
    ON combat_session (campaign_id)
    WHERE status <> 'ENDED';

CREATE TABLE IF NOT EXISTS session_participant (
    id                   BIGSERIAL PRIMARY KEY,
    session_id           BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    pc_id                BIGINT REFERENCES pc (id) ON DELETE CASCADE,   -- NULL = ad-hoc NPC
    owner_user_id        UUID,                                          -- PC owner; NULL for DM-added NPC
    display_name         TEXT NOT NULL,
    initiative           SMALLINT,                                      -- rolled total entered by the DM
    init_rolled          BOOLEAN NOT NULL DEFAULT FALSE,
    order_index          SMALLINT NOT NULL DEFAULT 0,                   -- server-computed sort position
    npc_hp_current       SMALLINT,                                      -- NPC-only; PC HP lives on pc row
    npc_hp_max           SMALLINT,
    npc_hp_temp          SMALLINT,
    npc_conditions       TEXT,                                          -- NPC-only JSON conditions
    death_save_successes SMALLINT NOT NULL DEFAULT 0,                   -- session-only (later phase)
    death_save_failures  SMALLINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_participant_session_id ON session_participant (session_id);
