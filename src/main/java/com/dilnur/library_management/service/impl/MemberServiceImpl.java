package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.MemberMapper;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public MemberResponse createMember(MemberRequest memberRequest) {
        log.info("Creating member: firstName={}, lastName={}, email={}",
                memberRequest.firstName(), memberRequest.lastName(), memberRequest.email());
        Member member = memberMapper.toEntity(memberRequest);
        Member savedMember = memberRepository.save(member);
        log.info("Member created successfully with id={}", savedMember.getId());

        return memberMapper.toResponse(savedMember);

    }

    @Override
    public MemberResponse getMemberById(UUID memberId) {
        log.debug("Fetching member with id={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        log.debug("Member fetched successfully with id={}", memberId);

        return memberMapper.toResponse(member);
    }

    @Override
    public Page<MemberResponse> getAllMembers(Pageable pageable) {
        log.debug("Fetching all members: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<Member> memberPage = memberRepository.findAll(pageable);
        log.debug("Fetched {} members (page {} of {})", memberPage.getNumberOfElements(),
                pageable.getPageNumber(), memberPage.getTotalPages());
        return memberMapper.toResponsePage(memberPage);
    }

    @Override
    public void deleteMember(UUID memberId) {
        log.info("Deleting member with id={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        boolean hasActiveLoans = loanRepository.existsByMemberAndStatusIn(
                member,
                List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)
        );
        if (hasActiveLoans) {
            throw new BusinessRuleException(
                    "Cannot delete member with id=" + memberId +
                            ": member has active or overdue loans"
            );
        }

        boolean hasPendingReservations = reservationRepository.existsByMemberAndStatusIn(
                member,
                List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)
        );
        if (hasPendingReservations) {
            throw new BusinessRuleException(
                    "Cannot delete member with id=" + memberId +
                            ": member has active reservations"
            );
        }

        memberRepository.delete(member);
        log.info("Member deleted successfully with id={}", memberId);
    }

    @Override
    public MemberResponse updateMember(UUID memberId, MemberRequest memberRequest) {
        log.info("Updating member with id={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        member.setFirstName(memberRequest.firstName());
        member.setLastName(memberRequest.lastName());
        member.setEmail(memberRequest.email());

        Member updatedMember = memberRepository.save(member);

        log.info("Member updated successfully with id={}", memberId);

        return memberMapper.toResponse(updatedMember);
    }

    @Override
    public Member getMemberEntityById(UUID uuid) {
        log.debug("Fetching member entity with id={}", uuid);

        return memberRepository.findById(uuid)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + uuid));
    }


}
