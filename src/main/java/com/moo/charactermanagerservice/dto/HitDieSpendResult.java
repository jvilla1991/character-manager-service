package com.moo.charactermanagerservice.dto;

/**
 * Result of spending one hit die during a short rest: the server-rolled die
 * value, the healing actually applied (1d[hitDie] + CON mod, floored at 1,
 * then capped at max HP), the PC's new current HP, and the new spent-dice
 * count — everything the client's toast and vitals strip need without a
 * refetch.
 */
public record HitDieSpendResult(
        int roll,
        int healed,
        Short hpCurrent,
        Short hitDiceUsed
) {}
