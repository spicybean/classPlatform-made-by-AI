package com.uniclass.domain.attendance.dto;

import com.uniclass.domain.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttendanceStatusUpdateDto {

    @NotNull(message = "출결 상태를 선택해 주세요.")
    private AttendanceStatus status;

    private String remark;
}
