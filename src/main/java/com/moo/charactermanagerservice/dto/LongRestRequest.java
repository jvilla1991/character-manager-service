package com.moo.charactermanagerservice.dto;

/**
 * Optional body when the DM opens a long rest phase. {@code undisturbed} seeds
 * the phase's fatigue-relief option (default true); the DM can still change it
 * until the rest finishes.
 */
public record LongRestRequest(Boolean undisturbed) {}
