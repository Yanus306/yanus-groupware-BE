package com.yanus.attendance.team;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeTeamRepository implements TeamRepository {

    private final Map<Long, Team> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Team save(Team team) {
        ReflectionTestUtils.setField(team, "id", sequence++);
        store.put(team.getId(), team);
        return team;
    }

    @Override
    public Optional<Team> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Team> findByName(String name) {
        return store.values().stream()
                .filter(team -> team.getName().equals(name))
                .findFirst();
    }

    @Override
    public boolean existsByMembersTeamId(Long teamId) {
        return false;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }


    @Override
    public List<Team> findAll() {
        return new ArrayList<>(store.values());
    }
}
