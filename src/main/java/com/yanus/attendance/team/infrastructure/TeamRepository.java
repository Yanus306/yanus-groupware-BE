package com.yanus.attendance.team.application;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.util.List;
import java.util.Optional;

public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(Long id);

    Optional<Team> findByName(TeamName name);

    List<Team> findALl();
}
