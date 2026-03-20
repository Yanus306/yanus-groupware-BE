package com.yanus.attendance.team.infrastructure;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class TeamJpaRepository implements TeamRepository {

    private final TeamJpaRepositoryPort port;

    public TeamJpaRepository(TeamJpaRepositoryPort port) {
        this.port = port;
    }

    @Override
    public Team save(Team team) {
        return port.save(team);
    }

    @Override
    public Optional<Team> findById(Long id) {
        return port.findById(id);
    }

    @Override
    public Optional<Team> findByName(TeamName name) {
        return port.findByName(name);
    }

    @Override
    public List<Team> findAll() {
        return port.findAll();
    }
}

interface TeamJpaRepositoryPort extends JpaRepository<Team, Long> {
    Optional<Team> findByName(TeamName name);
}
