package com.yanus.attendance.task.domain;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.team.domain.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long id;

    private String title;

    private LocalDate date;

    private LocalTime time;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    private boolean done;

    private boolean isTeamTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Member assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_members",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private List<Member> members = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Task createPersonal(Member createdBy, String title, LocalDate date, LocalTime time, TaskPriority priority, List<Member> members) {
        Task task = new Task();
        task.createdBy = createdBy;
        task.assignee = createdBy;
        task.title = title;
        task.date = date;
        task.time = time;
        task.priority = priority;
        task.done = false;
        task.isTeamTask = false;
        task.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        return task;
    }

    public static Task createTeam(Member createdBy, Member assignee, Team team, String title, LocalDate date, LocalTime time, TaskPriority priority, List<Member> members) {
        Task task = new Task();
        task.createdBy = createdBy;
        task.assignee = assignee;
        task.team = team;
        task.title = title;
        task.date = date;
        task.time = time;
        task.priority = priority;
        task.done = false;
        task.isTeamTask = true;
        task.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        return task;
    }

    public void toggleDone() {
        this.done = !this.done;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String title, LocalDate date, LocalTime time, TaskPriority priority) {
        this.title = title;
        this.date = date;
        this.time = time;
        this.priority = priority;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMembers(List<Member> members) {
        this.members.clear();
        if (members != null) {
            this.members.addAll(members);
        }
        this.updatedAt = LocalDateTime.now();
    }
}
