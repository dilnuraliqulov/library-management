package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.Fine;
import com.dilnur.library_management.entity.Loan;
import com.dilnur.library_management.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FineRepository extends JpaRepository<Fine, UUID> {

    Optional<Fine> findByLoan(Loan loan);

    List<Fine> findByLoanMemberAndStatus(Member member, FineStatus status);

    @Query("SELECT SUM(f.amount) FROM Fine f WHERE f.status = 'UNPAID'")
    BigDecimal totalUnpaidFines();

    @Query("SELECT SUM(f.amount) FROM Fine f WHERE f.status = 'PAID'")
    BigDecimal totalPaidFine();

}
