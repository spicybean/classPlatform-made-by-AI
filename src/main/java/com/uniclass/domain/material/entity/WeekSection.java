package com.uniclass.domain.material.entity;

import com.uniclass.domain.classroom.entity.ClassRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "week_sections", uniqueConstraints = {
        @UniqueConstraint(name = "UK_classroom_week_number", columnNames = {"classroom_id", "week_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeekSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 20)
    private SectionType sectionType = SectionType.LECTURE;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @Column(name = "notice_text", length = 500)
    private String noticeText;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden = false;

    @OneToMany(mappedBy = "weekSection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Material> materials = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.sectionType == null) {
            this.sectionType = SectionType.LECTURE;
        }
    }

    @Builder
    public WeekSection(ClassRoom classroom, int weekNumber, String title, String description,
                       SectionType sectionType, java.time.LocalDate startDate, java.time.LocalDate endDate,
                       String noticeText, boolean isHidden) {
        this.classroom = classroom;
        this.weekNumber = weekNumber;
        this.title = title != null ? title : (weekNumber + "주차");
        this.description = description;
        this.sectionType = sectionType != null ? sectionType : SectionType.LECTURE;
        this.startDate = startDate;
        this.endDate = endDate;
        this.noticeText = noticeText;
        this.isHidden = isHidden;
    }

    public void updateTitleAndDescription(String title, String description) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.description = description;
    }

    public void updateSectionInfo(String title, String description, SectionType sectionType,
                                  java.time.LocalDate startDate, java.time.LocalDate endDate,
                                  String noticeText, boolean isHidden) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.description = description;
        if (sectionType != null) {
            this.sectionType = sectionType;
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.noticeText = noticeText;
        this.isHidden = isHidden;
    }

    public void markAsHoliday(String reason) {
        this.sectionType = SectionType.HOLIDAY;
        this.noticeText = (reason != null && !reason.isBlank()) ? reason : "공휴일 또는 학사 일정으로 인한 휴강 주차입니다.";
    }

    public void markAsLecture() {
        this.sectionType = SectionType.LECTURE;
        this.noticeText = null;
    }
}
