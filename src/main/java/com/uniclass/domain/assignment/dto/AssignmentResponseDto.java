package com.uniclass.domain.assignment.dto;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AssignmentResponseDto {

    private Long id;
    private Long classroomId;
    private String classroomName;
    private Long weekSectionId;
    private Integer weekNumber;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private String formattedDueDate;
    private String dDayString;
    private int maxScore;
    private boolean allowLate;
    private boolean isExpired;
    private boolean canSubmitNow;

    // 첨부파일 (교수 제공 양식 등)
    private boolean hasAttachment;
    private String attachmentOriginalFilename;
    private String formattedAttachmentSize;

    // 통계 (교수용)
    private long totalSubmissions;
    private long totalEnrolledStudents;

    // 학생 시점 개인 제출 정보
    private boolean isSubmitted;
    private Long submissionId;
    private String submittedFilename;
    private String formattedSubmittedFileSize;
    private LocalDateTime submittedAt;
    private String submissionStatus;
    private String submissionStatusDesc;
    private Integer score;
    private String feedback;
    private String studentNote;

    public static AssignmentResponseDto from(Assignment a, long totalSubmissions, long totalEnrolledStudents, Submission mySubmission) {
        String formattedSize = "";
        if (a.getAttachmentSize() > 0) {
            long size = a.getAttachmentSize();
            if (size < 1024) formattedSize = size + " B";
            else if (size < 1024 * 1024) formattedSize = String.format("%.1f KB", size / 1024.0);
            else formattedSize = String.format("%.1f MB", size / (1024.0 * 1024.0));
        }

        AssignmentResponseDtoBuilder builder = AssignmentResponseDto.builder()
                .id(a.getId())
                .classroomId(a.getClassroom().getId())
                .classroomName(a.getClassroom().getName())
                .weekSectionId(a.getWeekSection() != null ? a.getWeekSection().getId() : null)
                .weekNumber(a.getWeekSection() != null ? a.getWeekSection().getWeekNumber() : null)
                .title(a.getTitle())
                .description(a.getDescription())
                .dueDate(a.getDueDate())
                .formattedDueDate(a.getDueDate().toString().replace("T", " "))
                .dDayString(a.getFormattedDDay())
                .maxScore(a.getMaxScore())
                .allowLate(a.isAllowLate())
                .isExpired(a.isExpired())
                .canSubmitNow(a.canSubmitNow())
                .hasAttachment(a.getAttachmentPath() != null && !a.getAttachmentPath().isBlank())
                .attachmentOriginalFilename(a.getAttachmentOriginalFilename())
                .formattedAttachmentSize(formattedSize)
                .totalSubmissions(totalSubmissions)
                .totalEnrolledStudents(totalEnrolledStudents);

        if (mySubmission != null) {
            builder.isSubmitted(true)
                    .submissionId(mySubmission.getId())
                    .submittedFilename(mySubmission.getOriginalFilename())
                    .formattedSubmittedFileSize(mySubmission.getFormattedFileSize())
                    .submittedAt(mySubmission.getSubmittedAt())
                    .submissionStatus(mySubmission.getStatus().name())
                    .submissionStatusDesc(mySubmission.getStatus().getDescription())
                    .score(mySubmission.getScore())
                    .feedback(mySubmission.getFeedback())
                    .studentNote(mySubmission.getNote());
        } else {
            builder.isSubmitted(false);
        }

        return builder.build();
    }
}
