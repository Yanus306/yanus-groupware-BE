package com.yanus.attendance.leave.domain;

import java.util.List;
import java.util.Optional;

public interface LeaveRepository {

    LeaveRequest save(LeaveRequest leaveRequest);

    Optional<LeaveRequest> findById(Long id);

    List<LeaveRequest> findAllByMemberId(Long memberId);

    List<LeaveRequest> findAllByTeamId(Long teamId);
}
