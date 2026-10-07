package com.uniclass.domain.library.dto;

import com.uniclass.domain.library.entity.LibraryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class CreateLibraryItemDto {

    @NotBlank(message = "자료 제목은 필수입니다.")
    @Size(max = 150, message = "자료 제목은 최대 150자까지 입력 가능합니다.")
    private String title;

    @Size(max = 1000, message = "설명은 최대 1000자까지 입력 가능합니다.")
    private String description;

    @NotNull(message = "카테고리를 선택해 주세요.")
    private LibraryCategory category = LibraryCategory.LECTURE_NOTE;

    private MultipartFile file;
}
