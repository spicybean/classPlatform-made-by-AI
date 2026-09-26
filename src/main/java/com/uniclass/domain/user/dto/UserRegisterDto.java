package com.uniclass.domain.user.dto;

import com.uniclass.domain.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRegisterDto {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "이름을 입력해 주세요.")
    private String name;

    @NotBlank(message = "학번 또는 교번을 입력해 주세요.")
    @Pattern(regexp = "^[0-9A-Za-z]{4,20}$", message = "학번/교번은 4~20자리 영문 또는 숫자여야 합니다.")
    private String studentNo;

    @NotNull(message = "가입 구분을 선택해 주세요.")
    private Role role;
}
