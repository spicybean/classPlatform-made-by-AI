package com.uniclass.domain.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateClassRoomDto {

    @NotBlank(message = "수업명을 입력해 주세요.")
    @Size(max = 100, message = "수업명은 최대 100자까지 가능합니다.")
    private String name;

    @NotBlank(message = "학수번호(과목코드)를 입력해 주세요.")
    @Size(max = 30, message = "과목코드는 최대 30자까지 가능합니다.")
    private String courseCode;

    @NotBlank(message = "학기를 입력해 주세요.")
    @Size(max = 20, message = "학기는 최대 20자까지 가능합니다.")
    private String semester;

    @NotBlank(message = "분반을 입력해 주세요.")
    @Size(max = 20, message = "분반은 최대 20자까지 가능합니다.")
    private String division;

    @Size(max = 2000, message = "수업 소개는 최대 2000자까지 가능합니다.")
    private String description;
}
