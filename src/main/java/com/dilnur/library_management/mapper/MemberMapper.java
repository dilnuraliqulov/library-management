package com.dilnur.library_management.mapper;

import com.dilnur.library_management.dto.request.MemberRequest;
import com.dilnur.library_management.dto.response.MemberResponse;
import com.dilnur.library_management.entity.Member;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    Member toEntity(MemberRequest memberRequest);

    MemberResponse toResponse(Member member);

    default Page<MemberResponse> toResponsePage(Page<Member> memberPage) {
        return memberPage.map(this::toResponse);
    }

    List<MemberResponse> toResponseList(List<Member> members);
}
