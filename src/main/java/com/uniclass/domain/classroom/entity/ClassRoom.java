package com.uniclass.domain.classroom.entity;

import com.uniclass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "classrooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String courseCode; // 학수번호 (예: CS101)

    @Column(nullable = false, length = 20)
    private String semester; // 학기 (예: 2026-1학기)

    @Column(nullable = false, length = 20)
    private String division; // 분반 (예: 01분반)

    @Column(columnDefinition = "TEXT")
    private String description; // 수업 소개/공지

    @Column(nullable = false, unique = true, length = 10)
    private String inviteCode; // 6자리 랜덤 초대 코드

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor; // 담당 교수

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false; // 학기 종료 후 아카이빙(보관) 여부

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public ClassRoom(String name, String courseCode, String semester, String division,
                     String description, String inviteCode, User instructor) {
        this.name = name;
        this.courseCode = courseCode;
        this.semester = semester;
        this.division = division;
        this.description = description;
        this.inviteCode = inviteCode;
        this.instructor = instructor;
        this.archived = false;
    }

    public void archive() {
        this.archived = true;
    }
}
