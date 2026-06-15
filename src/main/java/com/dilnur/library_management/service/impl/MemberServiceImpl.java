package com.dilnur.library_management.service.impl;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import com.dilnur.library_management.entity.Member;
import com.dilnur.library_management.mapper.MemberMapper;
import com.dilnur.library_management.repository.MemberRepository;
import com.dilnur.library_management.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Override
    public MemberResponse createMember(MemberRequest memberRequest) {
        Member member = memberMapper.toEntity(memberRequest);
        Member savedMember = memberRepository.save(member);
        return memberMapper.toResponse(savedMember);

    }

    @Override
    public MemberResponse getMemberById(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        return memberMapper.toResponse(member);
    }

    @Override
    public Page<MemberResponse> getAllMembers(Pageable pageable) {
        Page<Member> memberPage = memberRepository.findAll(pageable);
        return memberMapper.toResponsePage(memberPage);
    }

    @Override
    public void deleteMember(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        memberRepository.delete(member);

    }

    @Override
    public MemberResponse updateMember(UUID memberId, MemberRequest memberRequest) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found with id: " + memberId));

        member.setFirstName(memberRequest.firstName());
        member.setLastName(memberRequest.lastName());
        member.setEmail(memberRequest.email());

        Member updatedMember = memberRepository.save(member);

        return memberMapper.toResponse(updatedMember);
    }
}
