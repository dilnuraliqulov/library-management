package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.Book;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.dilnur.library_management.entity.Loan;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    int countByMemberAndStatusIn(Member member, List<LoanStatus> statuses);

    Optional<Loan> findByMemberAndBookAndStatus(Member member, Book book, LoanStatus status);

    List<Loan> findByStatus(LoanStatus status);

    List<Loan> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);


    @Query("SELECT DISTINCT l.member FROM Loan l where l.status = 'OVERDUE'")
    List<Member> findMembersWithOverdueLoans();

}
