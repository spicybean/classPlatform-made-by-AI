package com.uniclass.domain.user.entity;

public enum Role {
    ROLE_STUDENT("학생"),
    ROLE_INSTRUCTOR("교수"),
    ROLE_TA("조교"),
    ROLE_ALUMNI("수료/졸업생");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
