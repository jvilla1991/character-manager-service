package com.moo.charactermanagerservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A character's uploaded portrait image (V62), one row per character keyed by the PC's id.
 * Deliberately not mapped as a relation on {@link PC}: loading or saving a character never
 * touches the image bytes, and deleting a character leaves the row to the database's
 * {@code ON DELETE CASCADE}. The cache key clients use is {@link PC#getPortraitVersion()}.
 */
@Entity
@Table(name = "pc_portrait")
@Getter
@Setter
public class PcPortrait {
    @Id
    @Column(name = "pc_id")
    private Long pcId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    // bytea. No @Lob: on PostgreSQL that would map to a large-object oid instead.
    @Column(name = "image", nullable = false)
    private byte[] image;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
