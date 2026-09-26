package com.uniclass.domain.classroom.entity;

public enum EnrollmentStatus {
    ENROLLED("수강 중"),
    DROPPED("수강 취소"),
    COMPLETED("이수 완료");

    private final String description;

    EnrollmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
