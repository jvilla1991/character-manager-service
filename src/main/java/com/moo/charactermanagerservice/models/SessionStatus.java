package com.moo.charactermanagerservice.models;

/**
 * Lifecycle of a {@link CombatSession}. Persisted as its name (TEXT) via
 * {@code @Enumerated(EnumType.STRING)} — see {@code V8__session_mode.sql}.
 *
 * <ul>
 *   <li>{@code LOBBY}  — created; players are joining and picking PCs.</li>
 *   <li>{@code ACTIVE} — initiative locked; turns are being advanced.</li>
 *   <li>{@code ENDED}  — finished; HP/conditions already persisted on the PCs.</li>
 * </ul>
 */
public enum SessionStatus {
    LOBBY,
    ACTIVE,
    ENDED
}
