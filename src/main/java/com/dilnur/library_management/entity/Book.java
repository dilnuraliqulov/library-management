package com.dilnur.library_management.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "available_copies >= 0 AND available_copies <= total_copies")

public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false,length = 255)
    private String title;

    @Column(nullable = false,unique = true,length = 20)
    private String isbn;

    @Column(length = 100)
    private String genre;

    @Column(nullable = false)
    private int publicationYear;

    private double price;

    @Column(nullable = false)
    private int total_copies;

    @Column(nullable = false)
    private int available_copies;

}
