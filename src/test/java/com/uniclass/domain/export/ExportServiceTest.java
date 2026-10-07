package com.uniclass.domain.export;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.entity.SubmissionStatus;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.export.service.ExportService;
import com.uniclass.domain.grade.dto.GradeBookDto;
import com.uniclass.domain.grade.service.GradeService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private ClassRoomRepository classRoomRepository;

    @Mock
    private GradeService gradeService;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ExportService exportService;

    private ClassRoom classRoom;
    private User student1;
    private Assignment assignment1;
    private Submission submission1;

    @BeforeEach
    void setUp() {
        User instructor = User.builder()
                .email("prof@univ.ac.kr")
                .name("김교수")
                .studentNo("PROF001")
                .role(Role.ROLE_INSTRUCTOR)
                .build();
        ReflectionTestUtils.setField(instructor, "id", 1L);

        classRoom = ClassRoom.builder()
                .name("데이터베이스")
                .courseCode("CS101")
                .semester("2026-2학기")
                .division("01분반")
                .instructor(instructor)
                .inviteCode("DB1234")
                .build();
        ReflectionTestUtils.setField(classRoom, "id", 10L);

        student1 = User.builder()
                .email("student1@univ.ac.kr")
                .name("홍길동")
                .studentNo("20260001")
                .role(Role.ROLE_STUDENT)
                .build();
        ReflectionTestUtils.setField(student1, "id", 100L);

        assignment1 = Assignment.builder()
                .classroom(classRoom)
                .title("SQL 과제 1")
                .maxScore(100)
                .dueDate(LocalDateTime.now().plusDays(3))
                .build();
        ReflectionTestUtils.setField(assignment1, "id", 50L);

        submission1 = Submission.builder()
                .assignment(assignment1)
                .student(student1)
                .originalFilename("sql_hw1.pdf")
                .filePath("assignments/sql_hw1.pdf")
                .fileSize(1024L)
                .status(SubmissionStatus.GRADED)
                .build();
        submission1.grade(95, "매우 훌륭한 쿼리 작성입니다.");
        ReflectionTestUtils.setField(submission1, "id", 200L);
        ReflectionTestUtils.setField(submission1, "submittedAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("성적표 CSV 내보내기 시 UTF-8 BOM과 헤더 및 학생 점수 데이터가 정상 생성된다")
    void generateGradesCsv_success() {
        // given
        GradeBookDto.GradeItemHeaderDto header = GradeBookDto.GradeItemHeaderDto.builder()
                .assignmentId(50L)
                .title("SQL 과제 1")
                .maxScore(100)
                .build();

        GradeBookDto.StudentGradeRowDto row = GradeBookDto.StudentGradeRowDto.builder()
                .studentId(100L)
                .studentNo("20260001")
                .studentName("홍길동")
                .email("student1@univ.ac.kr")
                .scores(Map.of(50L, 95))
                .totalEarnedScore(95)
                .totalMaxScore(100)
                .assignmentPercentage(95.0)
                .attendanceRate(100.0)
                .totalCompositeScore(96.0)
                .letterGrade("A+")
                .build();

        GradeBookDto gradeBook = GradeBookDto.builder()
                .classroomId(10L)
                .classroomName("데이터베이스")
                .assignmentHeaders(List.of(header))
                .studentRows(List.of(row))
                .totalStudents(1)
                .classAverageScore(96.0)
                .build();

        given(classRoomRepository.findById(10L)).willReturn(Optional.of(classRoom));
        given(gradeService.getGradeBook(10L, 1L, true)).willReturn(gradeBook);

        // when
        byte[] csvBytes = exportService.generateGradesCsv(10L, 1L);

        // then
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvContent).startsWith("\uFEFF"); // UTF-8 BOM 확인
        assertThat(csvContent).contains("학번,이름,이메일,SQL 과제 1 (100점),과제 총점,과제 만점,과제 달성률(%),출석률(%),최종 환산점수(100점 만점),학점 등급");
        assertThat(csvContent).contains("20260001,홍길동,student1@univ.ac.kr,95,95,100,95.0,100.0,96.0,A+");
    }

    @Test
    @DisplayName("존재하지 않는 수업의 성적표 내보내기 시 예외가 발생한다")
    void generateGradesCsv_classNotFound_throwsException() {
        given(classRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> exportService.generateGradesCsv(999L, 1L));
    }

    @Test
    @DisplayName("제출물이 없을 때 전체 제출물 ZIP 내보내기 시 README 안내 파일이 포함된다")
    void generateSubmissionsZip_empty_hasReadme() throws IOException {
        given(classRoomRepository.findById(10L)).willReturn(Optional.of(classRoom));
        given(submissionRepository.findAllByClassroomIdWithStudentAndAssignment(10L)).willReturn(List.of());

        byte[] zipBytes = exportService.generateSubmissionsZip(10L);

        List<String> entryNames = extractZipEntryNames(zipBytes);
        assertThat(entryNames).containsExactly("README.txt");
    }

    @Test
    @DisplayName("제출물이 존재할 때 전체 제출물 ZIP 내에 과제명 폴더 구조로 파일이 압축된다")
    void generateSubmissionsZip_withFiles_success() throws IOException {
        given(classRoomRepository.findById(10L)).willReturn(Optional.of(classRoom));
        given(submissionRepository.findAllByClassroomIdWithStudentAndAssignment(10L)).willReturn(List.of(submission1));
        given(fileStorageService.loadAsResource(anyString()))
                .willReturn(new ByteArrayResource("dummy pdf content".getBytes(StandardCharsets.UTF_8)));

        byte[] zipBytes = exportService.generateSubmissionsZip(10L);

        List<String> entryNames = extractZipEntryNames(zipBytes);
        assertThat(entryNames).hasSize(1);
        assertThat(entryNames.get(0)).isEqualTo("SQL 과제 1/20260001_홍길동_sql_hw1.pdf");
    }

    @Test
    @DisplayName("학생 포트폴리오 ZIP 내보내기 시 피드백 요약 텍스트와 원본 파일이 모두 포함된다")
    void generateStudentPortfolioZip_success() throws IOException {
        given(classRoomRepository.findById(10L)).willReturn(Optional.of(classRoom));
        given(userRepository.findById(100L)).willReturn(Optional.of(student1));
        given(submissionRepository.findAllByClassroomIdAndStudentIdWithAssignment(10L, 100L)).willReturn(List.of(submission1));
        given(fileStorageService.loadAsResource(anyString()))
                .willReturn(new ByteArrayResource("dummy pdf content".getBytes(StandardCharsets.UTF_8)));

        byte[] zipBytes = exportService.generateStudentPortfolioZip(10L, 100L);

        List<String> entryNames = extractZipEntryNames(zipBytes);
        assertThat(entryNames).contains("과제제출_및_피드백_요약.txt", "SQL 과제 1_sql_hw1.pdf");
    }

    private List<String> extractZipEntryNames(byte[] zipBytes) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }
}
