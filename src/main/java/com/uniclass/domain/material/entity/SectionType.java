package com.uniclass.domain.material.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SectionType {
    LECTURE("일반 강의", "blue", "fa-chalkboard-user"),
    HOLIDAY("휴강 / 공휴일", "amber", "fa-umbrella-beach"),
    EXAM("시험 주간", "purple", "fa-pen-to-square"),
    MAKEUP("보강 주차", "emerald", "fa-clock-rotate-left"),
    SPECIAL("특강 / 발표", "rose", "fa-bullhorn");

    private final String description;
    private final String badgeColor;
    private final String icon;
}
