package com.yanus.attendance.attendance.infrastructure.workschedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.attendance.application.dto.workschedule.MemberWorkScheduleResponse;
import com.yanus.attendance.attendance.application.workschedule.AdminWorkScheduleCache;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yanus.work-schedule-cache.enabled", havingValue = "true")
public class RedisAdminWorkScheduleCache implements AdminWorkScheduleCache {

    private static final String KEY_PREFIX = "yanus:work-schedules:all:v1:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<MemberWorkScheduleResponse>> RESPONSE_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Override
    public List<MemberWorkScheduleResponse> getForAdmin(
            Long actorId, Supplier<List<MemberWorkScheduleResponse>> load) {
        String key = KEY_PREFIX + authorizeAndGetRevision(actorId);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                return mapper.readValue(value, RESPONSE_TYPE);
            }
        } catch (DataAccessException | JsonProcessingException ex) {
            return load.get();
        }

        List<MemberWorkScheduleResponse> result = load.get();
        try {
            redis.opsForValue().setIfAbsent(key, mapper.writeValueAsString(result), TTL);
        } catch (DataAccessException | JsonProcessingException ex) {
            // PostgreSQL provided the response even when the optional cache write failed.
        }
        return result;
    }

    private String authorizeAndGetRevision(Long actorId) {
        return jdbc.query("""
                select m.role, r.revision
                from member m cross join work_schedule_cache_revision r
                where m.member_id = ? and r.singleton
                """, rows -> {
            if (!rows.next()) {
                throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
            }
            if (!MemberRole.ADMIN.name().equals(rows.getString("role"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return rows.getString("revision");
        }, actorId);
    }
}
