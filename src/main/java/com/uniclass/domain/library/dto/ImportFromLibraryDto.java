package com.uniclass.domain.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportFromLibraryDto {

    @NotNull(message = "가져올 보관함 자료를 선택해 주세요.")
    private Long libraryItemId;

    @NotNull(message = "배치할 주차를 선택해 주세요.")
    @Min(value = 1, message = "주차는 1 이상이어야 합니다.")
    private Integer weekNumber;

    @Size(max = 150, message = "자료 제목은 최대 150자까지 입력 가능합니다.")
    private String customTitle;

    @Size(max = 1000, message = "설명은 최대 1000자까지 입력 가능합니다.")
    private String customDescription;
}
