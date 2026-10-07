package com.uniclass.domain.material.dto;

import com.uniclass.domain.material.entity.SectionType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWeekSectionDto {

    @Size(max = 100, message = "주차 제목은 최대 100자까지 가능합니다.")
    private String title;

    @Size(max = 255, message = "주차 설명은 최대 255자까지 가능합니다.")
    private String description;

    private SectionType sectionType;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Size(max = 500, message = "휴강/시험 공지 문구는 최대 500자까지 가능합니다.")
    private String noticeText;
    private boolean isHidden;
}
