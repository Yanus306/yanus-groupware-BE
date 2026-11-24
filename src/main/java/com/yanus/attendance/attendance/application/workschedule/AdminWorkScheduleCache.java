package com.yanus.attendance.attendance.application.workschedule;

import com.yanus.attendance.attendance.application.dto.workschedule.MemberWorkScheduleResponse;
import java.util.List;
import java.util.function.Supplier;

public interface AdminWorkScheduleCache {

    List<MemberWorkScheduleResponse> getForAdmin(Long actorId, Supplier<List<MemberWorkScheduleResponse>> load);
}
