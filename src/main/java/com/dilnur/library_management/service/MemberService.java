package com.dilnur.library_management.service;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MemberService {

    MemberResponse createMember(MemberRequest memberRequest);

    MemberResponse getMemberById(UUID memberId);

    Page<MemberResponse> getAllMembers(Pageable pageable);

    void deleteMember(UUID memberId);

    MemberResponse updateMember(UUID memberId, MemberRequest memberRequest);
}
