package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.application.ChannelService;
import com.yanus.attendance.chat.presentation.dto.ChannelResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅 채널", description = "채팅 채널 목록 조회")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(channelService.findAll()));
    }
}
