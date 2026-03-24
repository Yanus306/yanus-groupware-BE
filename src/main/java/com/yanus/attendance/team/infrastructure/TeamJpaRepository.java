package com.yanus.attendance.team.infrastructure;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    public Optional<Team> findByName(String name) {
        return port.findByName(name);
    }

    @Override
    public boolean existsByMembersTeamId(Long teamId) {
        return port.existsMemberByTeamId(teamId);
    }

    @Override
    public void deleteById(Long id) {
        port.deleteById(id);
    }


    @Override
    public List<Team> findAll() {
        return port.findAll();
    }
}

interface TeamJpaRepositoryPort extends JpaRepository<Team, Long> {
    Optional<Team> findByName(String name);

    @Query("SELECT COUNT(m) > 0 FROM Member m WHERE m.team.id = :teamId")
    boolean existsMemberByTeamId(@Param("teamId") Long teamId);
}
