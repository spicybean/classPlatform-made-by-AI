package com.uniclass.domain.assignment.dto;

import com.uniclass.domain.assignment.entity.SubmissionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SpeedGraderStudentItemDto {
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String email;
    private Long submissionId;
    private SubmissionStatus status; // SUBMITTED, LATE_SUBMITTED, GRADED, or null (NOT_SUBMITTED)
    private Integer score;
    private String feedback;
    private String originalFilename;
    private Long fileSize;
    private String note;
    private LocalDateTime submittedAt;
    private String previewType; // "pdf", "image", "text", "other", "none"

    public boolean isSubmitted() {
        return submissionId != null;
    }

    public boolean isGraded() {
        return status == SubmissionStatus.GRADED;
    }
}
