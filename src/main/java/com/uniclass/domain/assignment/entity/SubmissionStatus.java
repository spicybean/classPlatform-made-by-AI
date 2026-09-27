package com.uniclass.domain.assignment.entity;

public enum SubmissionStatus {
    SUBMITTED("정상 제출"),
    LATE_SUBMITTED("지각 제출"),
    GRADED("채점 완료");

    private final String description;

    SubmissionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
