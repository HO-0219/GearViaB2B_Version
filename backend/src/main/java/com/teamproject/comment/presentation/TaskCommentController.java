package com.teamproject.comment.presentation;

import com.teamproject.comment.application.TaskCommentService;
import com.teamproject.comment.application.dto.CommentDtos.CommentResponse;
import com.teamproject.comment.application.dto.CommentDtos.CreateCommentRequest;
import com.teamproject.comment.application.dto.CommentDtos.UpdateCommentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskCommentController {
    private final TaskCommentService comments;
    public TaskCommentController(TaskCommentService comments) { this.comments = comments; }

    @GetMapping("/tasks/{taskId}/comments")
    List<CommentResponse> list(Authentication authentication, @PathVariable Long taskId) {
        return comments.list((Long) authentication.getPrincipal(), taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse create(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return comments.create((Long) authentication.getPrincipal(), taskId, request);
    }

    @PatchMapping("/comments/{commentId}")
    CommentResponse update(Authentication authentication, @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return comments.update((Long) authentication.getPrincipal(), commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable Long commentId,
            @RequestParam Long expectedVersion) {
        comments.delete((Long) authentication.getPrincipal(), commentId, expectedVersion);
    }
}
