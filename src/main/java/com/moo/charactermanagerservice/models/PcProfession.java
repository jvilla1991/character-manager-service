package com.moo.charactermanagerservice.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pc_profession")
@Getter
@Setter
public class PcProfession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long pcId;
    private String profession;
}
