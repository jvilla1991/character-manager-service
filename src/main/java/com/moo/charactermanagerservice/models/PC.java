package com.moo.charactermanagerservice.models;

import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Entity
@Table(name = "pc", schema = "character_manage")
public class PC implements Serializable {

    public PC() {}

    public PC(Builder builder) {
        this.id = builder.id;
        this.clazz = builder.clazz;
        this.level = builder.level;
        this.name = builder.name;
        this.playerName = builder.playerName;
        this.userId = builder.userId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String clazz;
    private Short level;
    private String name;
    private String playerName;
    private UUID userId;   // <- scalar, no @ManyToOne

    public void setId(Long id) {
        this.id = id;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }



    public static class Builder {
        private Long id;
        private String clazz;
        private Short level;
        private String name;
        private String playerName;
        private UUID userId;

        public static Builder newInstance() {
            return new Builder();
        }

        private Builder() {}

        public Builder setId(Long id) {
            this.id = id;
            return this;
        }

        public Builder setClazz(String clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder setLevel(Short level) {
            this.level = level;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setPlayerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder setUserId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public PC build() {
            return new PC(this);
        }
    }
}
