package com.moo.charactermanagerservice.dto;

import com.moo.charactermanagerservice.models.PC;

/**
 * DM-authorized update of a campaign member's PC. Wraps the full character
 * body alongside an optional DM-authored log description that replaces the
 * automatic before/after diff (see {@link com.moo.charactermanagerservice.services.PcActivityLogService#logDmEdit}
 * ) — blank or absent falls back to the existing auto-diff.
 */
public record UpdatePcAsDmRequest(PC pc, String description) {}
