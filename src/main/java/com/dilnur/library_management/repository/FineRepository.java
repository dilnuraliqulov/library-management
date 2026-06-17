package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.Fine;
import com.dilnur.library_management.entity.Loan;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.enums.MemberType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT SUM(f.amount) FROM Fine f WHERE f.status = 'PAID'")
    BigDecimal totalPaidFine();

    @Query("SELECT SUM(f.amount) FROM Fine f WHERE f.loan.member.memberType = :memberType AND f.status = 'UNPAID'")
    BigDecimal totalUnpaidFinesByMemberType(MemberType memberType);

    Page<Fine> findByLoanMemberId(UUID memberId, Pageable pageable);

    long countByStatus(FineStatus status);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.status = 'UNPAID'")
    BigDecimal totalUnpaidFines();

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.status = 'PAID'")
    BigDecimal totalPaidFines();
}
