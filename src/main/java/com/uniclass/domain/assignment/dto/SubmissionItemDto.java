package com.uniclass.domain.assignment.dto;

import com.uniclass.domain.assignment.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SubmissionItemDto {

    private Long id;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String originalFilename;
    private String formattedFileSize;
    private LocalDateTime submittedAt;
    private String status;
    private String statusDesc;
    private Integer score;
    private String feedback;
    private String note;

    public static SubmissionItemDto from(Submission s) {
        return SubmissionItemDto.builder()
                .id(s.getId())
                .studentId(s.getStudent().getId())
                .studentName(s.getStudent().getName())
                .studentNo(s.getStudent().getStudentNo())
                .originalFilename(s.getOriginalFilename())
                .formattedFileSize(s.getFormattedFileSize())
                .submittedAt(s.getSubmittedAt())
                .status(s.getStatus().name())
                .statusDesc(s.getStatus().getDescription())
                .score(s.getScore())
                .feedback(s.getFeedback())
                .note(s.getNote())
                .build();
    }
}
