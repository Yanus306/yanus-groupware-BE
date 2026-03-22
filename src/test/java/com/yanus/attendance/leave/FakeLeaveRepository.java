package com.yanus.attendance.leave;

import com.yanus.attendance.leave.domain.LeaveRepository;
import com.yanus.attendance.leave.domain.LeaveRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeLeaveRepository implements LeaveRepository {

    private final Map<Long, LeaveRequest> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public LeaveRequest save(LeaveRequest leaveRequest) {
        if (leaveRequest.getId() == null) {
            ReflectionTestUtils.setField(leaveRequest, "id", sequence++);
        }
        store.put(leaveRequest.getId(), leaveRequest);
        return leaveRequest;
    }

    @Override
    public Optional<LeaveRequest> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<LeaveRequest> findAllByMemberId(Long memberId) {
        return store.values().stream()
                .filter(r -> r.getMember().getId().equals(memberId))
                .toList();
    }

    @Override
    public List<LeaveRequest> findAllByTeamId(Long teamId) {
        return store.values().stream()
                .filter(r -> r.getMember().getTeam().getId().equals(teamId))
                .toList();
    }
}
