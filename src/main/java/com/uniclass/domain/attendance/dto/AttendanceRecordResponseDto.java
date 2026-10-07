package com.uniclass.domain.attendance.dto;

import com.uniclass.domain.attendance.entity.AttendanceRecord;
import com.uniclass.domain.attendance.entity.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class AttendanceRecordResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Long recordId;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private AttendanceStatus status;
    private String statusDescription;
    private String badgeClass;
    private String attendedAt;
    private String clientIp;
    private String remark;

    public static AttendanceRecordResponseDto from(AttendanceRecord record) {
        return AttendanceRecordResponseDto.builder()
                .recordId(record.getId())
                .studentId(record.getStudent().getId())
                .studentName(record.getStudent().getName())
                .studentNo(record.getStudent().getStudentNo())
                .status(record.getStatus())
                .statusDescription(record.getStatus().getDescription())
                .badgeClass(record.getStatus().getBadgeClass())
                .attendedAt(record.getAttendedAt() != null ? record.getAttendedAt().format(FORMATTER) : "-")
                .clientIp(record.getClientIp())
                .remark(record.getRemark())
                .build();
    }
}
