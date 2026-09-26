package com.uniclass.domain.material.dto;

import com.uniclass.domain.material.entity.WeekSection;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WeekSectionDto {

    private Long id;
    private int weekNumber;
    private String title;
    private String description;
    private List<MaterialResponseDto> materials;

    public static WeekSectionDto from(WeekSection section, List<MaterialResponseDto> materials) {
        return WeekSectionDto.builder()
                .id(section.getId())
                .weekNumber(section.getWeekNumber())
                .title(section.getTitle())
                .description(section.getDescription())
                .materials(materials)
                .build();
    }
}
