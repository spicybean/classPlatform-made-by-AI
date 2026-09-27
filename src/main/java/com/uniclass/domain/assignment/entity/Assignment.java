package com.uniclass.domain.assignment.entity;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.material.entity.WeekSection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_section_id")
    private WeekSection weekSection;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "max_score", nullable = false)
    private int maxScore = 100;

    @Column(name = "allow_late", nullable = false)
    private boolean allowLate = true;

    @Column(name = "attachment_path", length = 500)
    private String attachmentPath;

    @Column(name = "attachment_original_filename", length = 255)
    private String attachmentOriginalFilename;

    @Column(name = "attachment_size", nullable = false)
    private long attachmentSize = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Assignment(ClassRoom classroom, WeekSection weekSection, String title, String description,
                      LocalDateTime dueDate, int maxScore, boolean allowLate,
                      String attachmentPath, String attachmentOriginalFilename, long attachmentSize) {
        this.classroom = classroom;
        this.weekSection = weekSection;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.maxScore = maxScore > 0 ? maxScore : 100;
        this.allowLate = allowLate;
        this.attachmentPath = attachmentPath;
        this.attachmentOriginalFilename = attachmentOriginalFilename;
        this.attachmentSize = attachmentSize;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.dueDate);
    }

    public boolean canSubmitNow() {
        return !isExpired() || this.allowLate;
    }

    public String getFormattedDDay() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(this.dueDate)) {
            Duration duration = Duration.between(this.dueDate, now);
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            if (days > 0) {
                return "마감 (" + days + "일 " + hours + "시간 전)";
            }
            return "마감 (" + hours + "시간 전)";
        } else {
            Duration duration = Duration.between(now, this.dueDate);
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            if (days > 0) {
                return "D-" + days + " (" + hours + "시간 남음)";
            } else if (hours > 0) {
                return "D-Day (" + hours + "시간 남음)";
            } else {
                return "D-Day (" + duration.toMinutesPart() + "분 남음)";
            }
        }
    }
}
