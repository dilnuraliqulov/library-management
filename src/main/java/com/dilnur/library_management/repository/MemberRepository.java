package com.dilnur.library_management.repository;

import com.dilnur.library_management.entity.Enum.MemberStatus;
import com.dilnur.library_management.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByIdAndStatus(UUID id, MemberStatus status);

}
