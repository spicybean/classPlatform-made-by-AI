package com.uniclass.domain.classroom.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassRoomCardDto {
    private Long id;
    private String name;
    private String courseCode;
    private String semester;
    private String division;
    private String inviteCode;
    private String instructorName;
    private long studentCount;
    private boolean isArchived;
}
