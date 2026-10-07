package com.uniclass.domain.export.service;

import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.grade.dto.GradeBookDto;
import com.uniclass.domain.grade.service.GradeService;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {

    private final ClassRoomRepository classRoomRepository;
    private final GradeService gradeService;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * [교수용] 학기 성적표를 UTF-8 BOM 기반 CSV 바이트 배열로 생성 (Excel 호환)
     */
    public byte[] generateGradesCsv(Long classroomId, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다. (ID: " + classroomId + ")"));

        GradeBookDto gradeBook = gradeService.getGradeBook(classroomId, instructorId, true);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM (MS Excel 한글 깨짐 방지)
        sb.append('\uFEFF');

        // 1. 헤더 생성
        sb.append("학번,이름,이메일");
        for (GradeBookDto.GradeItemHeaderDto header : gradeBook.getAssignmentHeaders()) {
            sb.append(",").append(escapeCsv(header.getTitle() + " (" + header.getMaxScore() + "점)"));
        }
        sb.append(",과제 총점,과제 만점,과제 달성률(%),출석률(%),최종 환산점수(100점 만점),학점 등급\n");

        // 2. 학생 데이터 행 생성
        for (GradeBookDto.StudentGradeRowDto row : gradeBook.getStudentRows()) {
            sb.append(escapeCsv(row.getStudentNo())).append(",");
            sb.append(escapeCsv(row.getStudentName())).append(",");
            sb.append(escapeCsv(row.getEmail()));

            for (GradeBookDto.GradeItemHeaderDto header : gradeBook.getAssignmentHeaders()) {
                Integer score = row.getScores().get(header.getAssignmentId());
                sb.append(",").append(score != null ? score : 0);
            }

            sb.append(",").append(row.getTotalEarnedScore());
            sb.append(",").append(row.getTotalMaxScore());
            sb.append(",").append(String.format("%.1f", row.getAssignmentPercentage()));
            sb.append(",").append(String.format("%.1f", row.getAttendanceRate()));
            sb.append(",").append(String.format("%.1f", row.getTotalCompositeScore()));
            sb.append(",").append(escapeCsv(row.getLetterGrade()));
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * [교수용] 해당 수업의 전체 과제 제출물을 폴더 구조로 묶어 ZIP 바이트 배열로 생성
     * 구조: [과제명]/[학번]_[학생이름]_[원본파일명]
     */
    public byte[] generateSubmissionsZip(Long classroomId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다. (ID: " + classroomId + ")"));

        List<Submission> submissions = submissionRepository.findAllByClassroomIdWithStudentAndAssignment(classroomId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<String> usedEntryNames = new HashSet<>();

            if (submissions.isEmpty()) {
                // 빈 압축 파일 에러 방지용 안내 파일
                String info = "수업명: " + classRoom.getName() + "\n제출된 과제가 없습니다.\n";
                ZipEntry entry = new ZipEntry("README.txt");
                zos.putNextEntry(entry);
                zos.write(info.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            } else {
                for (Submission s : submissions) {
                    if (s.getFilePath() == null || s.getFilePath().isBlank()) {
                        continue;
                    }

                    String sanitizedAssignmentTitle = sanitizeFilename(s.getAssignment().getTitle());
                    String sanitizedStudentNumber = sanitizeFilename(s.getStudent().getStudentNo());
                    String sanitizedStudentName = sanitizeFilename(s.getStudent().getName());
                    String sanitizedOriginalFilename = sanitizeFilename(s.getOriginalFilename());

                    String entryName = String.format("%s/%s_%s_%s",
                            sanitizedAssignmentTitle, sanitizedStudentNumber, sanitizedStudentName, sanitizedOriginalFilename);

                    // 중복 엔트리 이름 방어
                    String uniqueEntryName = getUniqueEntryName(usedEntryNames, entryName);
                    usedEntryNames.add(uniqueEntryName);

                    try {
                        Resource resource = fileStorageService.loadAsResource(s.getFilePath());
                        ZipEntry zipEntry = new ZipEntry(uniqueEntryName);
                        zos.putNextEntry(zipEntry);

                        try (InputStream is = resource.getInputStream()) {
                            is.transferTo(zos);
                        }
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.warn("제출물 ZIP 압축 중 파일 읽기 실패 - Submission ID: {}, File: {}", s.getId(), s.getFilePath(), e);
                    }
                }

                if (usedEntryNames.isEmpty()) {
                    String info = "수업명: " + classRoom.getName() + "\n제출된 과제 파일이 존재하지 않습니다.\n";
                    ZipEntry entry = new ZipEntry("README.txt");
                    zos.putNextEntry(entry);
                    zos.write(info.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            zos.finish();
        } catch (IOException e) {
            throw new IllegalStateException("과제 제출물 일괄 ZIP 압축 중 오류가 발생했습니다.", e);
        }

        return baos.toByteArray();
    }

    /**
     * [학생용] 특정 학생의 종강/졸업 포트폴리오(제출물 원본 + 피드백 요약)를 ZIP 바이트 배열로 생성
     */
    public byte[] generateStudentPortfolioZip(Long classroomId, Long studentId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다. (ID: " + classroomId + ")"));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 학생을 찾을 수 없습니다. (ID: " + studentId + ")"));

        List<Submission> submissions = submissionRepository.findAllByClassroomIdAndStudentIdWithAssignment(classroomId, studentId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<String> usedEntryNames = new HashSet<>();

            // 1. 피드백 요약 리포트 생성
            StringBuilder summarySb = new StringBuilder();
            summarySb.append("=================================================================\n");
            summarySb.append(" [UniClass] 학기 과제 제출 포트폴리오 & 피드백 리포트\n");
            summarySb.append(" 수업명 : ").append(classRoom.getName()).append(" (").append(classRoom.getCourseCode()).append(")\n");
            summarySb.append(" 학  생 : ").append(student.getName()).append(" (학번: ").append(student.getStudentNo()).append(")\n");
            summarySb.append(" 이메일 : ").append(student.getEmail()).append("\n");
            summarySb.append("=================================================================\n\n");

            if (submissions.isEmpty()) {
                summarySb.append("제출된 과제가 없습니다.\n");
            } else {
                for (int i = 0; i < submissions.size(); i++) {
                    Submission s = submissions.get(i);
                    summarySb.append(String.format("[%d] %s\n", i + 1, s.getAssignment().getTitle()));
                    summarySb.append(" - 제출 일시 : ").append(s.getSubmittedAt() != null ? s.getSubmittedAt().format(DATE_TIME_FORMATTER) : "기록 없음").append("\n");
                    summarySb.append(" - 제출 파일 : ").append(s.getOriginalFilename()).append("\n");
                    summarySb.append(" - 배점/취득 : ").append(s.getScore() != null ? s.getScore() + "점" : "채점 대기중").append(" / ").append(s.getAssignment().getMaxScore()).append("점 만점\n");
                    summarySb.append(" - 제출 상태 : ").append(s.getStatus().getDescription()).append("\n");
                    if (s.getNote() != null && !s.getNote().isBlank()) {
                        summarySb.append(" - 제출 메모 : ").append(s.getNote()).append("\n");
                    }
                    summarySb.append(" - 교수 피드백 :\n");
                    if (s.getFeedback() != null && !s.getFeedback().isBlank()) {
                        summarySb.append("   ").append(s.getFeedback().replace("\n", "\n   ")).append("\n");
                    } else {
                        summarySb.append("   (등록된 피드백이 없습니다.)\n");
                    }
                    summarySb.append("-----------------------------------------------------------------\n\n");
                }
            }

            ZipEntry summaryEntry = new ZipEntry("과제제출_및_피드백_요약.txt");
            zos.putNextEntry(summaryEntry);
            zos.write(summarySb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. 학생 제출 원본 파일 추가
            for (Submission s : submissions) {
                if (s.getFilePath() == null || s.getFilePath().isBlank()) {
                    continue;
                }

                String sanitizedAssignmentTitle = sanitizeFilename(s.getAssignment().getTitle());
                String sanitizedOriginalFilename = sanitizeFilename(s.getOriginalFilename());
                String entryName = String.format("%s_%s", sanitizedAssignmentTitle, sanitizedOriginalFilename);

                String uniqueEntryName = getUniqueEntryName(usedEntryNames, entryName);
                usedEntryNames.add(uniqueEntryName);

                try {
                    Resource resource = fileStorageService.loadAsResource(s.getFilePath());
                    ZipEntry zipEntry = new ZipEntry(uniqueEntryName);
                    zos.putNextEntry(zipEntry);

                    try (InputStream is = resource.getInputStream()) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                } catch (Exception e) {
                    log.warn("학생 포트폴리오 ZIP 파일 읽기 실패 - Submission ID: {}, File: {}", s.getId(), s.getFilePath(), e);
                }
            }

            zos.finish();
        } catch (IOException e) {
            throw new IllegalStateException("학생 포트폴리오 ZIP 압축 중 오류가 발생했습니다.", e);
        }

        return baos.toByteArray();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String getUniqueEntryName(Set<String> usedNames, String candidate) {
        if (!usedNames.contains(candidate)) {
            return candidate;
        }
        int extIndex = candidate.lastIndexOf('.');
        String baseName = extIndex != -1 ? candidate.substring(0, extIndex) : candidate;
        String extension = extIndex != -1 ? candidate.substring(extIndex) : "";

        int count = 1;
        while (usedNames.contains(baseName + " (" + count + ")" + extension)) {
            count++;
        }
        return baseName + " (" + count + ")" + extension;
    }
}
