package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.entity.enums.LoanStatus;
import com.dilnur.library_management.entity.enums.MemberStatus;
import com.dilnur.library_management.entity.enums.MemberType;
import com.dilnur.library_management.entity.enums.ReservationStatus;
import com.dilnur.library_management.exception.BusinessRuleException;
import com.dilnur.library_management.mapper.MemberMapper;
import com.dilnur.library_management.repository.LoanRepository;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.repository.ReservationRepository;
import com.dilnur.library_management.service.impl.MemberServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock MemberRepository memberRepository;
    @Mock MemberMapper memberMapper;
    @Mock LoanRepository loanRepository;
    @Mock ReservationRepository reservationRepository;

    @InjectMocks
    MemberServiceImpl memberService;

    private Member member;
    private MemberRequest memberRequest;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setId(UUID.randomUUID());
        member.setFirstName("John");
        member.setLastName("Doe");
        member.setEmail("john.doe@example.com");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMemberType(MemberType.STANDARD);
        member.setUnpaidFinesTotal(BigDecimal.ZERO);

        memberRequest = new MemberRequest("John", "Doe", "john.doe@example.com", MemberType.STANDARD);
    }


    @Nested
    @DisplayName("createMember()")
    class CreateMember {

        @Test
        @DisplayName("successfully creates a member and returns response")
        void createMember_success() {
            given(memberMapper.toEntity(memberRequest)).willReturn(member);
            given(memberRepository.save(member)).willReturn(member);
            MemberResponse expected = mock(MemberResponse.class);
            given(memberMapper.toResponse(member)).willReturn(expected);

            MemberResponse result = memberService.createMember(memberRequest);

            assertThat(result).isEqualTo(expected);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("new member starts with ACTIVE status and zero unpaid fines")
        void createMember_defaultStatusIsActive() {
            given(memberMapper.toEntity(memberRequest)).willReturn(member);
            given(memberRepository.save(member)).willReturn(member);
            given(memberMapper.toResponse(member)).willReturn(mock(MemberResponse.class));

            memberService.createMember(memberRequest);

            verify(memberRepository).save(argThat(m ->
                    m.getStatus() == MemberStatus.ACTIVE
                            && m.getUnpaidFinesTotal().compareTo(BigDecimal.ZERO) == 0));
        }
    }


    @Nested
    @DisplayName("getMemberById()")
    class GetMemberById {

        @Test
        @DisplayName("returns member response when found")
        void getMemberById_found() {
            MemberResponse expected = mock(MemberResponse.class);
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(memberMapper.toResponse(member)).willReturn(expected);

            MemberResponse result = memberService.getMemberById(member.getId());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when member not found")
        void getMemberById_notFound_throws() {
            UUID id = UUID.randomUUID();
            given(memberRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getMemberById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Member not found");
        }
    }


    @Nested
    @DisplayName("getMemberEntityById()")
    class GetMemberEntityById {

        @Test
        @DisplayName("returns member entity when found")
        void getMemberEntityById_found() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

            Member result = memberService.getMemberEntityById(member.getId());

            assertThat(result).isEqualTo(member);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when member not found")
        void getMemberEntityById_notFound_throws() {
            UUID id = UUID.randomUUID();
            given(memberRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getMemberEntityById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Member not found");
        }
    }


    @Nested
    @DisplayName("updateMember()")
    class UpdateMember {

        @Test
        @DisplayName("successfully updates member fields and returns updated response")
        void updateMember_success() {
            MemberRequest updateRequest = new MemberRequest("Jane", "Smith", "jane@example.com", MemberType.STANDARD);

            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(memberRepository.save(member)).willReturn(member);
            MemberResponse expected = mock(MemberResponse.class);
            given(memberMapper.toResponse(member)).willReturn(expected);

            MemberResponse result = memberService.updateMember(member.getId(), updateRequest);

            assertThat(result).isEqualTo(expected);
            assertThat(member.getFirstName()).isEqualTo("Jane");
            assertThat(member.getLastName()).isEqualTo("Smith");
            assertThat(member.getEmail()).isEqualTo("jane@example.com");
        }

        @Test
        @DisplayName("does not change member status or unpaid fines total during update")
        void updateMember_doesNotAlterStatusOrFines() {
            member.setStatus(MemberStatus.BLOCKED);
            member.setUnpaidFinesTotal(new BigDecimal("5000.00"));

            MemberRequest updateRequest = new MemberRequest("Jane", "Smith", "jane@example.com", MemberType.STANDARD);

            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(memberRepository.save(member)).willReturn(member);
            given(memberMapper.toResponse(member)).willReturn(mock(MemberResponse.class));

            memberService.updateMember(member.getId(), updateRequest);

            assertThat(member.getStatus()).isEqualTo(MemberStatus.BLOCKED);
            assertThat(member.getUnpaidFinesTotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
        }

        @Test
        @DisplayName("throws EntityNotFoundException when member not found")
        void updateMember_notFound_throws() {
            UUID id = UUID.randomUUID();
            given(memberRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.updateMember(id, memberRequest))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(memberRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("deleteMember()")
    class DeleteMember {

        @Test
        @DisplayName("successfully deletes member with no active loans or reservations")
        void deleteMember_success() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(false);
            given(reservationRepository.existsByMemberAndStatusIn(
                    member, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(false);

            memberService.deleteMember(member.getId());

            verify(memberRepository).delete(member);
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has ACTIVE loans")
        void deleteMember_hasActiveLoans_throws() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(true);

            assertThatThrownBy(() -> memberService.deleteMember(member.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active or overdue loans");

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has OVERDUE loans")
        void deleteMember_hasOverdueLoans_throws() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            // existsByMemberAndStatusIn covers both ACTIVE and OVERDUE in one call
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(true);

            assertThatThrownBy(() -> memberService.deleteMember(member.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active or overdue loans");

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has PENDING reservations")
        void deleteMember_hasPendingReservations_throws() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(false);
            given(reservationRepository.existsByMemberAndStatusIn(
                    member, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(true);

            assertThatThrownBy(() -> memberService.deleteMember(member.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active reservations");

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when member has NOTIFIED reservations")
        void deleteMember_hasNotifiedReservations_throws() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(false);
            given(reservationRepository.existsByMemberAndStatusIn(
                    member, List.of(ReservationStatus.PENDING, ReservationStatus.NOTIFIED)))
                    .willReturn(true);

            assertThatThrownBy(() -> memberService.deleteMember(member.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active reservations");

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when member not found")
        void deleteMember_notFound_throws() {
            UUID id = UUID.randomUUID();
            given(memberRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.deleteMember(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("loan check runs before reservation check (fail-fast order)")
        void deleteMember_loanCheckRunsFirst() {
            given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
            given(loanRepository.existsByMemberAndStatusIn(
                    member, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .willReturn(true);

            assertThatThrownBy(() -> memberService.deleteMember(member.getId()))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(reservationRepository);
        }
    }
}