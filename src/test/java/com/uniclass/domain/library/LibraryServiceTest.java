package com.uniclass.domain.library;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.library.dto.CreateLibraryItemDto;
import com.uniclass.domain.library.dto.ImportFromLibraryDto;
import com.uniclass.domain.library.dto.LibraryItemResponseDto;
import com.uniclass.domain.library.dto.LibrarySummaryDto;
import com.uniclass.domain.library.entity.LibraryCategory;
import com.uniclass.domain.library.entity.LibraryItem;
import com.uniclass.domain.library.repository.LibraryItemRepository;
import com.uniclass.domain.library.service.LibraryService;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class LibraryServiceTest {

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private LibraryItemRepository libraryItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private MaterialRepository materialRepository;

    private User profA;
    private User profB;
    private ClassRoom classRoomA;

    @BeforeEach
    void setUp() {
        profA = userRepository.save(User.builder()
                .email("prof_a@test.com")
                .password("encoded_pass")
                .name("김교수")
                .studentNo("P2026001")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        profB = userRepository.save(User.builder()
                .email("prof_b@test.com")
                .password("encoded_pass")
                .name("박교수")
                .studentNo("P2026002")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        classRoomA = classRoomRepository.save(ClassRoom.builder()
                .instructor(profA)
                .name("자료구조론")
                .courseCode("CS101")
                .description("자료구조 기초 강의")
                .inviteCode("DATA1234")
                .semester("2026-1학기")
                .division("01분반")
                .build());
    }

    @Test
    @DisplayName("교수는 개인 보관함에 강의 교안 파일을 성공적으로 업로드하고 통계를 조회할 수 있다")
    void uploadItem_and_getSummary_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture01.pdf",
                "application/pdf",
                "PDF Content".getBytes(StandardCharsets.UTF_8)
        );

        CreateLibraryItemDto dto = new CreateLibraryItemDto();
        dto.setTitle("1주차 OT 슬라이드");
        dto.setDescription("강의 개요 및 평가 기준 안내");
        dto.setCategory(LibraryCategory.LECTURE_NOTE);
        dto.setFile(file);

        Long itemId = libraryService.uploadItem(profA.getId(), dto);
        assertThat(itemId).isNotNull();

        LibraryItem item = libraryItemRepository.findById(itemId).orElseThrow();
        assertThat(item.getTitle()).isEqualTo("1주차 OT 슬라이드");
        assertThat(item.getCategory()).isEqualTo(LibraryCategory.LECTURE_NOTE);
        assertThat(item.getOriginalFilename()).isEqualTo("lecture01.pdf");
        assertThat(item.getDownloadCount()).isEqualTo(0);

        // 통계 검증
        LibrarySummaryDto summary = libraryService.getSummary(profA.getId());
        assertThat(summary.getTotalItems()).isEqualTo(1L);
        assertThat(summary.getTotalBytes()).isGreaterThan(0L);
        assertThat(summary.getCountByCategory().get("LECTURE_NOTE")).isEqualTo(1L);

        // 목록 조회 검증
        List<LibraryItemResponseDto> items = libraryService.getMyLibraryItems(profA.getId(), null, null);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTitle()).isEqualTo("1주차 OT 슬라이드");
    }

    @Test
    @DisplayName("첨부 파일이 비어있는 경우 보관함 업로드 시 예외가 발생한다 (Negative Test)")
    void uploadItem_emptyFile_throwsException() {
        CreateLibraryItemDto dto = new CreateLibraryItemDto();
        dto.setTitle("빈 파일 테스트");
        dto.setCategory(LibraryCategory.LECTURE_NOTE);
        dto.setFile(null);

        assertThrows(IllegalArgumentException.class, () ->
                libraryService.uploadItem(profA.getId(), dto));
    }

    @Test
    @DisplayName("보관함 소유 교수는 파일을 정상 다운로드할 수 있고 다운로드 횟수가 증가한다")
    void downloadItem_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", "Sample Content".getBytes(StandardCharsets.UTF_8));
        CreateLibraryItemDto dto = new CreateLibraryItemDto();
        dto.setTitle("다운로드 테스트");
        dto.setFile(file);
        Long itemId = libraryService.uploadItem(profA.getId(), dto);

        LibraryService.DownloadResult result = libraryService.downloadItem(itemId, profA.getId());
        assertThat(result.filename()).isEqualTo("sample.pdf");

        LibraryItem item = libraryItemRepository.findById(itemId).orElseThrow();
        assertThat(item.getDownloadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타 교수가 다른 교수의 보관함 자료를 다운로드하려 하면 BOLA 예외가 발생한다 (Negative Test)")
    void downloadItem_otherInstructor_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.pdf", "application/pdf", "Secret Content".getBytes(StandardCharsets.UTF_8));
        CreateLibraryItemDto dto = new CreateLibraryItemDto();
        dto.setTitle("김교수 개인 자료");
        dto.setFile(file);
        Long itemId = libraryService.uploadItem(profA.getId(), dto);

        // 박교수가 김교수 자료 다운로드 시도
        assertThrows(IllegalArgumentException.class, () ->
                libraryService.downloadItem(itemId, profB.getId()));
    }

    @Test
    @DisplayName("타 교수가 다른 교수의 보관함 자료를 삭제하려 하면 BOLA 예외가 발생한다 (Negative Test)")
    void deleteItem_otherInstructor_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "Test".getBytes(StandardCharsets.UTF_8));
        CreateLibraryItemDto dto = new CreateLibraryItemDto();
        dto.setTitle("김교수 시험지");
        dto.setFile(file);
        Long itemId = libraryService.uploadItem(profA.getId(), dto);

        // 박교수가 삭제 시도 -> 거부
        assertThrows(IllegalArgumentException.class, () ->
                libraryService.deleteItem(itemId, profB.getId()));

        assertThat(libraryItemRepository.findById(itemId)).isPresent();
    }

    @Test
    @DisplayName("교수는 개인 보관함 자료를 본인의 수업 1주차로 원클릭 복제/가져오기 할 수 있다")
    void importToClassroom_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "master_syllabus.pdf", "application/pdf", "Syllabus Content".getBytes(StandardCharsets.UTF_8));
        CreateLibraryItemDto uploadDto = new CreateLibraryItemDto();
        uploadDto.setTitle("표준 강의계획서 템플릿");
        uploadDto.setDescription("전공 공통 강의계획서");
        uploadDto.setCategory(LibraryCategory.LECTURE_NOTE);
        uploadDto.setFile(file);
        Long libraryItemId = libraryService.uploadItem(profA.getId(), uploadDto);

        ImportFromLibraryDto importDto = ImportFromLibraryDto.builder()
                .libraryItemId(libraryItemId)
                .weekNumber(1)
                .customTitle("1주차 수업 강의계획서 (배포용)")
                .customDescription("1주차 강의계획서입니다.")
                .build();

        Long materialId = libraryService.importToClassroom(classRoomA.getId(), profA.getId(), importDto);
        assertThat(materialId).isNotNull();

        Material material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getTitle()).isEqualTo("1주차 수업 강의계획서 (배포용)");
        assertThat(material.getDescription()).isEqualTo("1주차 강의계획서입니다.");
        assertThat(material.getWeekSection().getWeekNumber()).isEqualTo(1);
        assertThat(material.getClassroom().getId()).isEqualTo(classRoomA.getId());

        // 원본 보관함 자료도 유지되는지 확인
        assertThat(libraryItemRepository.findById(libraryItemId)).isPresent();
    }

    @Test
    @DisplayName("타인의 수업에 본인 보관함 자료를 가져오려 하거나 타인의 보관함 자료를 가져오려 하면 예외가 발생한다 (Negative Test)")
    void importToClassroom_unauthorized_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "Content".getBytes(StandardCharsets.UTF_8));
        CreateLibraryItemDto uploadDto = new CreateLibraryItemDto();
        uploadDto.setTitle("자료");
        uploadDto.setFile(file);
        Long profAItemId = libraryService.uploadItem(profA.getId(), uploadDto);

        ImportFromLibraryDto importDto = ImportFromLibraryDto.builder()
                .libraryItemId(profAItemId)
                .weekNumber(2)
                .build();

        // 1. 박교수가 김교수 수업에 가져오기 시도 -> 예외
        assertThrows(IllegalArgumentException.class, () ->
                libraryService.importToClassroom(classRoomA.getId(), profB.getId(), importDto));

        // 2. 김교수가 박교수의 보관함 자료를 김교수 수업에 가져오기 시도 -> 예외
        Long profBItemId = libraryService.uploadItem(profB.getId(), uploadDto);
        ImportFromLibraryDto invalidItemDto = ImportFromLibraryDto.builder()
                .libraryItemId(profBItemId)
                .weekNumber(2)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                libraryService.importToClassroom(classRoomA.getId(), profA.getId(), invalidItemDto));
    }
}
