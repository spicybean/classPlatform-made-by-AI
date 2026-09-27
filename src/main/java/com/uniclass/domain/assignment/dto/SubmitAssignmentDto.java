package com.uniclass.domain.assignment.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class SubmitAssignmentDto {

    private MultipartFile file;

    @Size(max = 1000, message = "제출 메모는 최대 1000자까지 작성 가능합니다.")
    private String note;
}
