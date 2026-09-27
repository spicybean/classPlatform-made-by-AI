package com.uniclass.domain.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class CreateAssignmentDto {

    @NotBlank(message = "과제 제목을 입력해 주세요.")
    @Size(max = 150, message = "과제 제목은 최대 150자까지 입력 가능합니다.")
    private String title;

    @Size(max = 5000, message = "과제 설명은 최대 5000자까지 입력 가능합니다.")
    private String description;

    @Min(value = 1, message = "주차는 1 이상이어야 합니다.")
    @Max(value = 16, message = "주차는 최대 16까지 선택 가능합니다.")
    private Integer weekNumber;

    @NotBlank(message = "마감 일시를 지정해 주세요.")
    private String dueDate; // yyyy-MM-dd'T'HH:mm

    @Min(value = 1, message = "배점은 최소 1점 이상이어야 합니다.")
    @Max(value = 1000, message = "배점은 최대 1000점까지 가능합니다.")
    private int maxScore = 100;

    private boolean allowLate = true;

    private MultipartFile attachment;
}
