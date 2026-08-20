package com.teamproject.comment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
    List<CommentMention> findAllByCommentIdOrderByIdAsc(Long commentId);
    void deleteAllByCommentId(Long commentId);
}
