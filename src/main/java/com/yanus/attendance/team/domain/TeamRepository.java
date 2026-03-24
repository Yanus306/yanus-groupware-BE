package com.yanus.attendance.team.domain;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(Long id);

    Optional<Team> findByName(String name);

    List<Team> findAll();

    boolean existsByMembersTeamId(Long teamId);

    void deleteById(Long id);
}
