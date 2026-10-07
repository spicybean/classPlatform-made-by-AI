package com.uniclass.domain.attendance.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentAttendanceSummaryDto {
    private long totalSessions;
    private long presentCount;
    private long lateCount;
    private long absentCount;
    private long excusedCount;
    private double attendanceRate; // 출석률 (%)
    private List<AttendanceRecordResponseDto> myRecords;
    private AttendanceSessionResponseDto activeSession; // 현재 진행 중인 세션이 있다면
    private boolean alreadyAttendedActiveSession;
}
