package com.uniclass.domain.classroom.entity;

import com.uniclass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_enrollment_student_classroom", columnNames = {"student_id", "classroom_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime enrolledAt;

    @PrePersist
    public void prePersist() {
        this.enrolledAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = EnrollmentStatus.ENROLLED;
        }
    }

    @Builder
    public Enrollment(User student, ClassRoom classroom, EnrollmentStatus status) {
        this.student = student;
        this.classroom = classroom;
        this.status = status != null ? status : EnrollmentStatus.ENROLLED;
    }

    public void drop() {
        this.status = EnrollmentStatus.DROPPED;
    }

    public void complete() {
        this.status = EnrollmentStatus.COMPLETED;
    }

    public void reEnroll() {
        this.status = EnrollmentStatus.ENROLLED;
    }
}
