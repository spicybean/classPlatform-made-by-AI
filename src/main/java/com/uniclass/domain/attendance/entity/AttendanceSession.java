package com.uniclass.domain.attendance.entity;

import com.uniclass.domain.classroom.entity.ClassRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @Column(nullable = false)
    private int weekNumber; // 1~16주차

    @Column(nullable = false)
    private int roundNumber = 1; // 1차시, 2차시 등

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 4)
    private String authCode; // 4자리 인증번호

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime validUntil; // 정규 출석 마감 (예: 시작 + 3분)

    @Column(nullable = false)
    private LocalDateTime lateUntil; // 지각 인정 마감 (예: 시작 + 10분)

    @Column(nullable = false)
    private boolean active = true; // 세션 활성 여부

    @Column(nullable = false)
    private int timeoutMinutes = 3;

    @Builder
    public AttendanceSession(ClassRoom classroom, int weekNumber, int roundNumber,
                             String title, String authCode, LocalDateTime startedAt,
                             LocalDateTime validUntil, LocalDateTime lateUntil,
                             int timeoutMinutes) {
        this.classroom = classroom;
        this.weekNumber = weekNumber;
        this.roundNumber = roundNumber > 0 ? roundNumber : 1;
        this.title = title != null && !title.isBlank() ? title : weekNumber + "주차 " + this.roundNumber + "차시 출석";
        this.authCode = authCode;
        this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : 3;
        this.validUntil = validUntil != null ? validUntil : this.startedAt.plusMinutes(this.timeoutMinutes);
        this.lateUntil = lateUntil != null ? lateUntil : this.startedAt.plusMinutes(10);
        this.active = true;
    }

    public void close() {
        this.active = false;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(this.lateUntil) || !this.active;
    }

    public AttendanceStatus evaluateStatus(LocalDateTime requestTime) {
        if (!requestTime.isAfter(this.validUntil)) {
            return AttendanceStatus.PRESENT;
        } else if (!requestTime.isAfter(this.lateUntil)) {
            return AttendanceStatus.LATE;
        } else {
            return AttendanceStatus.ABSENT;
        }
    }
}
