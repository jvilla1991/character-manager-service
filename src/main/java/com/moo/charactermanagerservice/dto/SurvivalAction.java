package com.moo.charactermanagerservice.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A player's survival-condition improvement action (Darker Dungeons ch. 31). */
public enum SurvivalAction {
    EAT,             // consume a ration: −1 hunger
    DRINK,           // drink from a waterskin: −1 thirst
    SLEEP_GOOD,      // undisturbed night's sleep: −3 fatigue
    SLEEP_DISTURBED; // disturbed night's sleep: −1 fatigue

    /** Parse the request string (400 on anything unknown, matching validation style). */
    public static SurvivalAction parse(String raw) {
        if (raw != null) {
            for (SurvivalAction action : values()) {
                if (action.name().equalsIgnoreCase(raw.trim())) {
                    return action;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown survival action: " + raw);
    }
}
