package com.uniclass.domain.grade.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class GradeBookDto {

    private Long classroomId;
    private String classroomName;
    private List<GradeItemHeaderDto> assignmentHeaders;
    private List<StudentGradeRowDto> studentRows;

    // 통계 요약 (교수 전용)
    private int totalStudents;
    private Double classAverageScore;

    @Getter
    @Builder
    public static class GradeItemHeaderDto {
        private Long assignmentId;
        private String title;
        private int maxScore;
    }

    @Getter
    @Builder
    public static class StudentGradeRowDto {
        private Long studentId;
        private String studentName;
        private String studentNo;
        private String email;

        // assignmentId -> 취득 점수 (null이면 미제출 또는 미채점)
        private Map<Long, Integer> scores;

        // 제출 상태 표시: assignmentId -> status (SUBMITTED, GRADED, NOT_SUBMITTED)
        private Map<Long, String> submissionStatuses;

        private int totalEarnedScore;
        private int totalMaxScore;
        private double assignmentPercentage; // 과제 달성률 (%)

        private double attendanceRate;       // 출석률 (%)
        private double totalCompositeScore;  // 종합 점수 (100점 만점 환산: 과제 80% + 출석 20%)
        private String letterGrade;          // A+, A, B+, B, C+, C, F
    }
}
