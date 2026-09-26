package com.uniclass.domain.material.dto;

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
public class CreateMaterialDto {

    @Min(value = 1, message = "주차는 1주차 이상이어야 합니다.")
    @Max(value = 16, message = "주차는 최대 16주차까지 가능합니다.")
    private int weekNumber = 1;

    @NotBlank(message = "자료 제목을 입력해 주세요.")
    @Size(max = 150, message = "자료 제목은 최대 150자까지 입력 가능합니다.")
    private String title;

    @Size(max = 1000, message = "자료 설명은 최대 1000자까지 입력 가능합니다.")
    private String description;

    private MultipartFile file;

    private boolean isPublished = true;

    /**
     * 예약 공개 일시 (형식: YYYY-MM-DDTHH:mm, 예: 2026-03-15T09:00)
     */
    private String releaseAt;
}
