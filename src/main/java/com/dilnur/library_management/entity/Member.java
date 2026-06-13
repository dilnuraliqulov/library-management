package com.dilnur.library_management.entity;

import com.dilnur.library_management.entity.Enum.MemberStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false,length = 100)
    private String fName;

    @Column(nullable = false,length = 100)
    private String lName;

    @Column(nullable = false,unique = true,length = 150)
    private String email;

    @Column(nullable = false,updatable = false)
    private LocalDate createdAt = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(nullable = false)
    private BigDecimal unpaidFinesTotal  = BigDecimal.ZERO;

}
