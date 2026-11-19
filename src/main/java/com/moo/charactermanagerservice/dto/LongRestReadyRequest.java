package com.moo.charactermanagerservice.dto;

/** A player marks their own seated PC ready (or not ready) for the long rest. */
public record LongRestReadyRequest(Long pcId, Boolean ready) {}
