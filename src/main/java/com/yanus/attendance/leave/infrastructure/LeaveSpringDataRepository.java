package com.yanus.attendance.leave.infrastructure;

import com.yanus.attendance.leave.domain.LeaveRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveSpringDataRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findAllByMemberId(Long memberId);

    @Query("SELECT l FROM LeaveRequest l WHERE l.member.team.id = :teamId")
    List<LeaveRequest> findAllByTeamId(@Param("teamId") Long teamId);
}
