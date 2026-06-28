package com.moo.charactermanagerservice.dto;

import java.util.List;

/** DM request to replace the set of characters at the active shop. */
public record SetShopAttendeesRequest(List<Long> pcIds) {}
