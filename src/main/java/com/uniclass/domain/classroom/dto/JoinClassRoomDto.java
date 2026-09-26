package com.uniclass.domain.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JoinClassRoomDto {

    @NotBlank(message = "6자리 초대 코드를 입력해 주세요.")
    @Pattern(regexp = "^[0-9A-Za-z]{6}$", message = "초대 코드는 6자리 영문 또는 숫자여야 합니다.")
    private String inviteCode;
}
