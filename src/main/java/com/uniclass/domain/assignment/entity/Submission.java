package com.uniclass.domain.assignment.entity;

import com.uniclass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "submissions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_submission_assignment_student", columnNames = {"assignment_id", "student_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(length = 1000)
    private String note;

    private Integer score;

    @Column(length = 1000)
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.submittedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Submission(Assignment assignment, User student, String filePath,
                      String originalFilename, long fileSize, String note,
                      SubmissionStatus status) {
        this.assignment = assignment;
        this.student = student;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.note = note;
        this.status = status;
    }

    public void updateSubmission(String filePath, String originalFilename, long fileSize, String note, SubmissionStatus status) {
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.note = note;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void grade(int score, String feedback) {
        this.score = score;
        this.feedback = feedback;
        this.status = SubmissionStatus.GRADED;
    }

    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}
