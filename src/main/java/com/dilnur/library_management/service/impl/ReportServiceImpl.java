package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.dto.response.FineStatisticsResponse;
import com.dilnur.library_management.dto.response.MostBorrowedBookResponse;
import com.dilnur.library_management.dto.response.OverdueMemberResponse;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.enums.FineStatus;
import com.dilnur.library_management.entity.enums.MemberType;
import com.dilnur.library_management.repository.BookRepository;
import com.dilnur.library_management.repository.FineRepository;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;


    @Override
    public Page<MostBorrowedBookResponse> getMostBorrowedBooks(Pageable pageable) {
        return bookRepository.findMostBorrowedBooks(pageable)
                .map(book -> new MostBorrowedBookResponse(
                        book.getId(),
                        book.getTitle(),
                        book.getIsbn(),
                        book.getTotalCopies(),
                        book.getAvailableCopies(),
                        book.getTotalCopies() - book.getAvailableCopies() - book.getNotifiedBooks()
                ));
    }


    @Override
    public List<OverdueMemberResponse> getMembersWithOverdueLoans() {
        return loanRepository.findMembersWithOverdueLoansAndCount()
                .stream()
                .map(row -> {
                    Member member = (Member) row[0];
                    long overdueCount = (long) row[1];
                    return new OverdueMemberResponse(
                            member.getId(),
                            member.getFirstName(),
                            member.getLastName(),
                            member.getEmail(),
                            member.getUnpaidFinesTotal(),
                            (int) overdueCount
                    );
                })
                .toList();
    }


    @Override
    public FineStatisticsResponse getFineStatistics() {
        BigDecimal totalUnpaid = fineRepository.totalUnpaidFines();
        BigDecimal totalPaid = fineRepository.totalPaidFines();
        long unpaidCount = fineRepository.countByStatus(FineStatus.UNPAID);
        long paidCount = fineRepository.countByStatus(FineStatus.PAID);

        // breakdown by member type — for the optional member-type fine rate feature
        Map<String, BigDecimal> unpaidByMemberType = new LinkedHashMap<>();
        for (MemberType type : MemberType.values()) {
            BigDecimal amount = fineRepository.totalUnpaidFinesByMemberType(type);
            unpaidByMemberType.put(type.name(), amount != null ? amount : BigDecimal.ZERO);
        }

        return new FineStatisticsResponse(
                totalUnpaid != null ? totalUnpaid : BigDecimal.ZERO,
                totalPaid != null ? totalPaid : BigDecimal.ZERO,
                unpaidCount,
                paidCount,
                unpaidByMemberType
        );
    }
}
