package com.uniclass.domain.attendance.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceStatus {
    PRESENT("출석", "bg-emerald-50 text-emerald-700 border-emerald-200"),
    LATE("지각", "bg-amber-50 text-amber-700 border-amber-200"),
    ABSENT("결석", "bg-rose-50 text-rose-700 border-rose-200"),
    EXCUSED("공결", "bg-blue-50 text-blue-700 border-blue-200");

    private final String description;
    private final String badgeClass;
}
