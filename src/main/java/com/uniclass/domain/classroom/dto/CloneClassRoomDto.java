package com.uniclass.domain.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CloneClassRoomDto {

    private Long sourceClassroomId;

    @NotBlank(message = "새 수업명을 입력해 주세요.")
    @Size(max = 100, message = "수업명은 100자 이내여야 합니다.")
    private String newName;

    @NotBlank(message = "새 학기를 입력해 주세요.")
    @Size(max = 50, message = "학기는 50자 이내여야 합니다.")
    private String newSemester;

    @NotBlank(message = "새 분반을 입력해 주세요.")
    @Size(max = 20, message = "분반은 20자 이내여야 합니다.")
    private String newDivision;

    @NotNull(message = "새 학기 시작일을 선택해 주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate newStartDate;

    private boolean cloneMaterials = true;

    private boolean cloneAssignments = true;

    public CloneClassRoomDto(Long sourceClassroomId, String newName, String newSemester, String newDivision,
                             LocalDate newStartDate, boolean cloneMaterials, boolean cloneAssignments) {
        this.sourceClassroomId = sourceClassroomId;
        this.newName = newName;
        this.newSemester = newSemester;
        this.newDivision = newDivision;
        this.newStartDate = newStartDate;
        this.cloneMaterials = cloneMaterials;
        this.cloneAssignments = cloneAssignments;
    }
}
