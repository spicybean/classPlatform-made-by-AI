package com.uniclass.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitAttendanceDto {

    @NotBlank(message = "4자리 출석 인증번호를 입력해 주세요.")
    @Pattern(regexp = "^[0-9]{4}$", message = "인증번호는 4자리 숫자여야 합니다.")
    private String authCode;
}
