package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.application.MessageService;
import com.yanus.attendance.chat.presentation.dto.MessageCreateRequest;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/{channelId}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId,
            @RequestBody MessageCreateRequest request) {
        MessageResponse response = messageService.sendMessage(channelId, memberId, request.content(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping(value = "/{channelId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MessageResponse>> sendFileMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam("files") List<MultipartFile> files) {
        MessageResponse response = messageService.sendFileMessage(channelId, memberId, content, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
