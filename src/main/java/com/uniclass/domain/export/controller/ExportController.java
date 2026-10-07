package com.uniclass.domain.export.controller;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.export.service.ExportService;
import com.uniclass.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequestMapping("/classes/{classroomId}/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final ClassRoomService classRoomService;
    private final ClassRoomRepository classRoomRepository;

    /**
     * [교수용] 학기 성적표 CSV 파일 다운로드
     */
    @GetMapping("/grades-csv")
    public ResponseEntity<byte[]> exportGradesCsv(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        classRoomService.assertInstructor(classroomId, userDetails.getId());
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다."));

        byte[] csvData = exportService.generateGradesCsv(classroomId, userDetails.getId());

        String filename = URLEncoder.encode(classRoom.getName() + "_성적표.csv", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    /**
     * [교수용] 전체 과제 제출물 일괄 ZIP 압축 다운로드
     */
    @GetMapping("/submissions-zip")
    public ResponseEntity<byte[]> exportSubmissionsZip(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        classRoomService.assertInstructor(classroomId, userDetails.getId());
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다."));

        byte[] zipData = exportService.generateSubmissionsZip(classroomId);

        String filename = URLEncoder.encode(classRoom.getName() + "_제출물전체.zip", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipData);
    }

    /**
     * [학생용] 내 과제 제출물 및 교수 피드백 포트폴리오 ZIP 다운로드
     */
    @GetMapping("/portfolio-zip")
    public ResponseEntity<byte[]> exportStudentPortfolioZip(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        classRoomService.assertEnrolled(classroomId, userDetails.getId());
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다."));

        byte[] zipData = exportService.generateStudentPortfolioZip(classroomId, userDetails.getId());

        String filename = URLEncoder.encode(classRoom.getName() + "_" + userDetails.getName() + "_과제포트폴리오.zip", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipData);
    }
}
