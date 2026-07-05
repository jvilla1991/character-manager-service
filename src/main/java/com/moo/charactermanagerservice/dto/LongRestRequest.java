package com.moo.charactermanagerservice.dto;

/** DM triggers a party long rest; {@code undisturbed} decides the fatigue relief. */
public record LongRestRequest(Boolean undisturbed) {}
