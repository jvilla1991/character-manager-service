package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

/**
 * Body for creating a follower or updating one (owner or DM): its name and its
 * stat block — an opaque JSON object in the client's sheet shape.
 *
 * <p>{@code kind} is read on create only: {@code DM_GRANT} (also what a missing
 * value means, so a client from before V64 keeps working), {@code SUMMON} or
 * {@code FAMILIAR}. An update ignores it — a follower's kind never changes.
 */
public record FollowerRequest(String name, Map<String, Object> statBlock, String kind) {

    /** The constructor a request body is read through, whatever else is declared here. */
    @JsonCreator
    public FollowerRequest {}

    /** A body without a kind: a DM grant on create, and all an update reads. */
    public FollowerRequest(String name, Map<String, Object> statBlock) {
        this(name, statBlock, null);
    }
}
