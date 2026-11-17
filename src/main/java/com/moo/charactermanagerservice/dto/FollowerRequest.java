package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * Body for granting a follower (DM) or updating one (owner or DM): its name and
 * its stat block — an opaque JSON object in the client's sheet shape.
 */
public record FollowerRequest(String name, Map<String, Object> statBlock) {}
