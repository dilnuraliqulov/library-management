package com.dilnur.library_management.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "authors")
@Getter
@Setter
@NoArgsConstructor
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false,length = 100)
    private String fName;

    @Column(nullable = false,length = 100)
    private String lName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    public String getFullName() {
        return fName + " " + lName;
    }

}
