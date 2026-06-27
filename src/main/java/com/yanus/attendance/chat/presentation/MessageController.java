package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.application.MessageService;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅 메시지", description = "채널 메시지 목록 조회")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/{channelId}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            @PathVariable Long channelId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getMessages(channelId, pageable)));
    }
}
