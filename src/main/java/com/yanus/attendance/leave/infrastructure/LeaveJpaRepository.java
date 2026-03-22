package com.yanus.attendance.leave.infrastructure;

import com.yanus.attendance.leave.domain.LeaveRepository;
import com.yanus.attendance.leave.domain.LeaveRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LeaveJpaRepository implements LeaveRepository {

    private final LeaveSpringDataRepository repository;

    @Override
    public LeaveRequest save(LeaveRequest leaveRequest) {
        return repository.save(leaveRequest);
    }

    @Override
    public Optional<LeaveRequest> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<LeaveRequest> findAllByMemberId(Long memberId) {
        return repository.findAllByMemberId(memberId);
    }

    @Override
    public List<LeaveRequest> findAllByTeamId(Long teamId) {
        return repository.findAllByTeamId(teamId);
    }
}
