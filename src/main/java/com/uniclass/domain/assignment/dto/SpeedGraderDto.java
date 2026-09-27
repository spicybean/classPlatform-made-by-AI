package com.uniclass.domain.assignment.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SpeedGraderDto {
    private AssignmentResponseDto assignment;
    private List<SpeedGraderStudentItemDto> students;
    private SpeedGraderStudentItemDto currentStudent;
    private Long prevStudentId;
    private Long nextStudentId;
    private int currentIndex; // 1-based index (e.g. 1 of 25)
    private int totalStudents;
    private int gradedCount;
    private int submittedCount;
}
