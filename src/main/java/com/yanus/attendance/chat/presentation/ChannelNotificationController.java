package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.application.ChannelNotificationService;
import com.yanus.attendance.chat.presentation.dto.ChannelMuteRequest;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅 알림 설정", description = "채널별 알림 on/off")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelNotificationController {

    private final ChannelNotificationService channelNotificationService;

    @GetMapping("/notifications/muted")
    public ResponseEntity<ApiResponse<List<Long>>> getMutedChannels(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(channelNotificationService.getMutedChannelIds(memberId)));
    }

    @PutMapping("/{channelId}/notifications")
    public ResponseEntity<ApiResponse<Void>> setMuted(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId,
            @RequestBody ChannelMuteRequest request) {
        channelNotificationService.setMuted(memberId, channelId, request.muted());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
