package com.uniclass.domain.attendance.dto;

import com.uniclass.domain.attendance.entity.AttendanceSession;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Builder
public class AttendanceSessionResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long sessionId;
    private Long classroomId;
    private String classroomName;
    private int weekNumber;
    private int roundNumber;
    private String title;
    private String authCode; // 4자리 코드
    private String startedAt;
    private String validUntil;
    private String lateUntil;
    private boolean active;
    private int timeoutMinutes;
    private long remainingSeconds;
    private long totalStudents;
    private long presentCount;
    private long lateCount;
    private long absentCount;
    private long excusedCount;
    private List<AttendanceRecordResponseDto> records;

    public static AttendanceSessionResponseDto from(AttendanceSession session,
                                                    long totalStudents,
                                                    long presentCount,
                                                    long lateCount,
                                                    long absentCount,
                                                    long excusedCount,
                                                    List<AttendanceRecordResponseDto> records,
                                                    LocalDateTime now) {
        long remaining = 0;
        if (session.isActive() && now.isBefore(session.getValidUntil())) {
            remaining = Math.max(0, Duration.between(now, session.getValidUntil()).getSeconds());
        }

        return AttendanceSessionResponseDto.builder()
                .sessionId(session.getId())
                .classroomId(session.getClassroom().getId())
                .classroomName(session.getClassroom().getName())
                .weekNumber(session.getWeekNumber())
                .roundNumber(session.getRoundNumber())
                .title(session.getTitle())
                .authCode(session.getAuthCode())
                .startedAt(session.getStartedAt().format(FORMATTER))
                .validUntil(session.getValidUntil().format(FORMATTER))
                .lateUntil(session.getLateUntil().format(FORMATTER))
                .active(session.isActive())
                .timeoutMinutes(session.getTimeoutMinutes())
                .remainingSeconds(remaining)
                .totalStudents(totalStudents)
                .presentCount(presentCount)
                .lateCount(lateCount)
                .absentCount(absentCount)
                .excusedCount(excusedCount)
                .records(records)
                .build();
    }
}
