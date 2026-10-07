package com.uniclass.domain.material.dto;

import com.uniclass.domain.material.entity.SectionType;
import com.uniclass.domain.material.entity.WeekSection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Builder
public class WeekSectionDto {

    private Long id;
    private int weekNumber;
    private String title;
    private String description;

    private SectionType sectionType;
    private String sectionTypeDesc;
    private String sectionTypeBadgeColor;
    private String sectionTypeIcon;

    private LocalDate startDate;
    private LocalDate endDate;
    private String formattedDateRange;

    private String noticeText;
    private boolean isHidden;

    private boolean isHoliday;
    private boolean isExam;
    private boolean isMakeup;

    private List<MaterialResponseDto> materials;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM.dd");

    public static WeekSectionDto from(WeekSection section, List<MaterialResponseDto> materials) {
        SectionType type = section.getSectionType() != null ? section.getSectionType() : SectionType.LECTURE;

        String formattedRange = "";
        if (section.getStartDate() != null && section.getEndDate() != null) {
            formattedRange = section.getStartDate().format(DATE_FORMATTER) + " ~ " + section.getEndDate().format(DATE_FORMATTER);
        } else if (section.getStartDate() != null) {
            formattedRange = section.getStartDate().format(DATE_FORMATTER) + " ~";
        }

        return WeekSectionDto.builder()
                .id(section.getId())
                .weekNumber(section.getWeekNumber())
                .title(section.getTitle())
                .description(section.getDescription())
                .sectionType(type)
                .sectionTypeDesc(type.getDescription())
                .sectionTypeBadgeColor(type.getBadgeColor())
                .sectionTypeIcon(type.getIcon())
                .startDate(section.getStartDate())
                .endDate(section.getEndDate())
                .formattedDateRange(formattedRange)
                .noticeText(section.getNoticeText())
                .isHidden(section.isHidden())
                .isHoliday(type == SectionType.HOLIDAY)
                .isExam(type == SectionType.EXAM)
                .isMakeup(type == SectionType.MAKEUP)
                .materials(materials)
                .build();
    }
}
