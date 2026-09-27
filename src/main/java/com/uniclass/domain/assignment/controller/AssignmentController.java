package com.uniclass.domain.assignment.controller;

import com.uniclass.domain.assignment.dto.AssignmentResponseDto;
import com.uniclass.domain.assignment.dto.CreateAssignmentDto;
import com.uniclass.domain.assignment.dto.SubmissionItemDto;
import com.uniclass.domain.assignment.dto.SubmitAssignmentDto;
import com.uniclass.domain.assignment.service.AssignmentService;
import com.uniclass.domain.assignment.service.AssignmentService.DownloadResult;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/classes/{classroomId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final ClassRoomService classRoomService;

    public AssignmentController(AssignmentService assignmentService, ClassRoomService classRoomService) {
        this.assignmentService = assignmentService;
        this.classRoomService = classRoomService;
    }

    /**
     * 과제 출제 폼 (교수 전용)
     */
    @GetMapping("/new")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String newAssignmentPage(@PathVariable("classroomId") Long classroomId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    Model model) {
        ClassRoom classRoom = classRoomService.getClassRoomDetail(classroomId);
        if (!classRoom.getInstructor().getId().equals(userDetails.getId())) {
            return "redirect:/?error=forbidden";
        }

        model.addAttribute("classroom", classRoom);
        model.addAttribute("form", new CreateAssignmentDto());
        return "assignment/create";
    }

    /**
     * 과제 출제 처리 (교수 전용)
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String createAssignment(@PathVariable("classroomId") Long classroomId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   @Valid @ModelAttribute("form") CreateAssignmentDto form,
                                   BindingResult bindingResult,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        ClassRoom classRoom = classRoomService.getClassRoomDetail(classroomId);
        if (!classRoom.getInstructor().getId().equals(userDetails.getId())) {
            return "redirect:/?error=forbidden";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("classroom", classRoom);
            return "assignment/create";
        }

        try {
            Long assignmentId = assignmentService.createAssignment(classroomId, userDetails.getId(), form);
            redirectAttributes.addFlashAttribute("assignmentSuccessMessage", "과제가 성공적으로 등록되었습니다.");
            return "redirect:/classes/" + classroomId + "/assignments/" + assignmentId;
        } catch (Exception e) {
            model.addAttribute("classroom", classRoom);
            model.addAttribute("errorMessage", e.getMessage());
            return "assignment/create";
        }
    }

    /**
     * 과제 상세 페이지 (교수/학생 공통, 권한별 뷰 분기)
     */
    @GetMapping("/{assignmentId}")
    public String assignmentDetail(@PathVariable("classroomId") Long classroomId,
                                   @PathVariable("assignmentId") Long assignmentId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {
        // 인가 검증: 수강생 또는 담당 교수만 접근 가능
        if (!classRoomService.isUserEnrolledOrInstructor(classroomId, userDetails.getId())) {
            return "redirect:/?error=not_enrolled";
        }

        ClassRoom classRoom = classRoomService.getClassRoomDetail(classroomId);
        boolean isInstructor = classRoom.getInstructor().getId().equals(userDetails.getId());

        AssignmentResponseDto assignment = assignmentService.getAssignmentDetail(classroomId, assignmentId, userDetails.getId(), isInstructor);
        model.addAttribute("classroom", classRoom);
        model.addAttribute("isInstructor", isInstructor);
        model.addAttribute("assignment", assignment);
        model.addAttribute("submitForm", new SubmitAssignmentDto());

        if (isInstructor) {
            List<SubmissionItemDto> submissions = assignmentService.getSubmissionsForAssignment(classroomId, assignmentId, userDetails.getId());
            model.addAttribute("submissions", submissions);
        }

        return "assignment/detail";
    }

    /**
     * 학생 과제 제출 및 재제출 (학생 전용)
     */
    @PostMapping("/{assignmentId}/submit")
    @PreAuthorize("hasRole('ROLE_STUDENT')")
    public String submitAssignment(@PathVariable("classroomId") Long classroomId,
                                   @PathVariable("assignmentId") Long assignmentId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   @ModelAttribute("submitForm") SubmitAssignmentDto submitForm,
                                   RedirectAttributes redirectAttributes) {
        try {
            assignmentService.submitAssignment(classroomId, assignmentId, userDetails.getId(), submitForm);
            redirectAttributes.addFlashAttribute("submissionSuccessMessage", "과제가 성공적으로 제출되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("submissionErrorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "/assignments/" + assignmentId;
    }

    /**
     * 학생 제출 파일 다운로드 (제출 학생 본인 및 담당 교수)
     */
    @GetMapping("/{assignmentId}/submissions/{submissionId}/download")
    public ResponseEntity<Resource> downloadSubmission(@PathVariable("classroomId") Long classroomId,
                                                        @PathVariable("assignmentId") Long assignmentId,
                                                        @PathVariable("submissionId") Long submissionId,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isInstructor = userDetails.getRole() == Role.ROLE_INSTRUCTOR;
        DownloadResult result = assignmentService.downloadSubmission(classroomId, assignmentId, submissionId, userDetails.getId(), isInstructor);

        String encodedFilename = UriUtils.encode(result.originalFilename(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(result.resource());
    }

    /**
     * 과제 첨부파일(교수 양식) 다운로드
     */
    @GetMapping("/{assignmentId}/attachment/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable("classroomId") Long classroomId,
                                                       @PathVariable("assignmentId") Long assignmentId,
                                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        DownloadResult result = assignmentService.downloadAssignmentAttachment(classroomId, assignmentId, userDetails.getId());

        String encodedFilename = UriUtils.encode(result.originalFilename(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(result.resource());
    }

    /**
     * 과제 채점 및 피드백 (교수 전용)
     */
    @PostMapping("/{assignmentId}/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String gradeSubmission(@PathVariable("classroomId") Long classroomId,
                                  @PathVariable("assignmentId") Long assignmentId,
                                  @PathVariable("submissionId") Long submissionId,
                                  @RequestParam("score") int score,
                                  @RequestParam(value = "feedback", required = false) String feedback,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        try {
            assignmentService.gradeSubmission(classroomId, assignmentId, submissionId, userDetails.getId(), score, feedback);
            redirectAttributes.addFlashAttribute("gradeSuccessMessage", "성적이 성공적으로 반영되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("gradeErrorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "/assignments/" + assignmentId;
    }
}
