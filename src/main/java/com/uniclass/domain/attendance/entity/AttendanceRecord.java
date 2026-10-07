package com.uniclass.domain.attendance.entity;

import com.uniclass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_session_student", columnNames = {"session_id", "student_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "attended_at", nullable = false)
    private LocalDateTime attendedAt;

    @Column(length = 45)
    private String clientIp;

    @Column(length = 200)
    private String remark; // 비고 (교수 수동 정정 사유 등)

    @Version
    private Long version;

    @Builder
    public AttendanceRecord(AttendanceSession session, User student, AttendanceStatus status,
                            LocalDateTime attendedAt, String clientIp, String remark) {
        this.session = session;
        this.student = student;
        this.status = status;
        this.attendedAt = attendedAt != null ? attendedAt : LocalDateTime.now();
        this.clientIp = clientIp;
        this.remark = remark;
    }

    public void updateStatus(AttendanceStatus newStatus, String remark) {
        this.status = newStatus;
        if (remark != null) {
            this.remark = remark;
        }
    }
}
