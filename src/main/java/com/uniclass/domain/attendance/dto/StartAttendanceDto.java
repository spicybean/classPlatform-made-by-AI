package com.uniclass.domain.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartAttendanceDto {

    @Min(value = 1, message = "주차는 1주차 이상이어야 합니다.")
    @Max(value = 16, message = "주차는 최대 16주차까지 가능합니다.")
    private int weekNumber = 1;

    @Min(value = 1, message = "차시는 1차시 이상이어야 합니다.")
    @Max(value = 10, message = "차시는 최대 10차시까지 가능합니다.")
    private int roundNumber = 1;

    @Min(value = 1, message = "유효 시간은 최소 1분 이상이어야 합니다.")
    @Max(value = 60, message = "유효 시간은 최대 60분까지 설정 가능합니다.")
    private int timeoutMinutes = 3; // 기본 3분
}
