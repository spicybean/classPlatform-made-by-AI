package com.uniclass.domain.attendance.controller;

import com.uniclass.domain.attendance.dto.AttendanceRecordResponseDto;
import com.uniclass.domain.attendance.dto.AttendanceSessionResponseDto;
import com.uniclass.domain.attendance.dto.AttendanceStatusUpdateDto;
import com.uniclass.domain.attendance.dto.StartAttendanceDto;
import com.uniclass.domain.attendance.dto.StudentAttendanceSummaryDto;
import com.uniclass.domain.attendance.dto.SubmitAttendanceDto;
import com.uniclass.domain.attendance.service.AttendanceService;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/classes/{classroomId}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ClassRoomService classRoomService;

    /**
     * 출석 메인 대시보드 (교수/학생 공통 뷰 분기)
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String attendanceDashboard(@PathVariable("classroomId") Long classroomId,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (!classRoomService.isUserEnrolledOrInstructor(classroomId, userDetails.getId())) {
            return "redirect:/?error=not_enrolled";
        }

        ClassRoom classRoom = classRoomService.getClassRoomDetail(classroomId);
        boolean isInstructor = classRoom.getInstructor() != null && classRoom.getInstructor().getId().equals(userDetails.getId());

        model.addAttribute("classroom", classRoom);
        model.addAttribute("isInstructor", isInstructor);

        if (isInstructor) {
            // 교수 뷰: 세션 목록 및 시작 폼
            List<AttendanceSessionResponseDto> sessions = attendanceService.getSessionsForClassroom(classroomId, userDetails.getId(), true);
            model.addAttribute("sessions", sessions);
            model.addAttribute("startForm", new StartAttendanceDto());
            return "attendance/dashboard";
        } else {
            // 학생 뷰: 본인 출석 요약 및 4자리 입력 폼
            StudentAttendanceSummaryDto summary = attendanceService.getStudentSummary(classroomId, userDetails.getId());
            model.addAttribute("summary", summary);
            model.addAttribute("submitForm", new SubmitAttendanceDto());
            return "attendance/student_dashboard";
        }
    }

    /**
     * 4자리 출석 체크 시작 (교수 전용)
     */
    @PostMapping("/start")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String startSession(@PathVariable("classroomId") Long classroomId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               @Valid @ModelAttribute("startForm") StartAttendanceDto form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/classes/" + classroomId + "/attendance";
        }

        try {
            Long sessionId = attendanceService.startSession(classroomId, userDetails.getId(), form);
            redirectAttributes.addFlashAttribute("successMessage", "출석 체크가 시작되었습니다. 학생들에게 4자리 인증번호를 안내하세요.");
            return "redirect:/classes/" + classroomId + "/attendance/sessions/" + sessionId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/classes/" + classroomId + "/attendance";
        }
    }

    /**
     * 대형 스크린 4자리 번호 표시 및 실시간 현황 팝업 화면 (교수 전용)
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String sessionView(@PathVariable("classroomId") Long classroomId,
                              @PathVariable("sessionId") Long sessionId,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        ClassRoom classRoom = classRoomService.getClassRoomDetail(classroomId);
        if (classRoom.getInstructor() == null || !classRoom.getInstructor().getId().equals(userDetails.getId())) {
            return "redirect:/?error=forbidden";
        }

        AttendanceSessionResponseDto session = attendanceService.getSessionDetail(classroomId, sessionId, userDetails.getId(), true);
        model.addAttribute("classroom", classRoom);
        model.addAttribute("attSession", session);
        model.addAttribute("session", session);
        return "attendance/session";
    }

    /**
     * 출석 세션 수동 마감 (교수 전용)
     */
    @PostMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String closeSession(@PathVariable("classroomId") Long classroomId,
                               @PathVariable("sessionId") Long sessionId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            attendanceService.closeSession(classroomId, sessionId, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "출석 세션이 마감되었습니다. 미출석 학생은 결석 처리되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "/attendance";
    }

    /**
     * 학생 4자리 출석 인증번호 입력 처리 (학생 전용)
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('ROLE_STUDENT')")
    public String submitAttendance(@PathVariable("classroomId") Long classroomId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   @Valid @ModelAttribute("submitForm") SubmitAttendanceDto form,
                                   BindingResult bindingResult,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/classes/" + classroomId + "/attendance";
        }

        try {
            String clientIp = getClientIp(request);
            AttendanceRecordResponseDto record = attendanceService.submitAttendance(
                    classroomId, userDetails.getId(), form.getAuthCode(), clientIp);

            redirectAttributes.addFlashAttribute("successMessage",
                    "[" + record.getStatusDescription() + "] 처리가 정상 완료되었습니다! (입력 시각: " + record.getAttendedAt() + ")");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "/attendance";
    }

    /**
     * 대형 팝업용 실시간 출석 현황 폴링 API (JSON)
     */
    @GetMapping("/sessions/{sessionId}/status")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getSessionStatus(@PathVariable("classroomId") Long classroomId,
                                              @PathVariable("sessionId") Long sessionId,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        boolean isInstructor = userDetails.getRole() == Role.ROLE_INSTRUCTOR;
        try {
            AttendanceSessionResponseDto session = attendanceService.getSessionDetail(classroomId, sessionId, userDetails.getId(), isInstructor);
            return ResponseEntity.ok(Map.of(
                    "active", session.isActive(),
                    "remainingSeconds", session.getRemainingSeconds(),
                    "totalStudents", session.getTotalStudents(),
                    "presentCount", session.getPresentCount(),
                    "lateCount", session.getLateCount(),
                    "absentCount", session.getAbsentCount(),
                    "records", session.getRecords()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 학생 출결 수동 정정 (교수 전용)
     */
    @PostMapping("/sessions/{sessionId}/records/{recordId}/update")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String updateRecord(@PathVariable("classroomId") Long classroomId,
                               @PathVariable("sessionId") Long sessionId,
                               @PathVariable("recordId") Long recordId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               @Valid @ModelAttribute AttendanceStatusUpdateDto form,
                               RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            attendanceService.updateRecordStatus(classroomId, sessionId, recordId, userDetails.getId(), form);
            redirectAttributes.addFlashAttribute("successMessage", "출결 상태가 성공적으로 변경되었습니다.");
        } catch (OptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "다른 사용자가 동시에 출결 정보를 수정했습니다. 새로고침 후 다시 시도해 주세요.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "/attendance/sessions/" + sessionId;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
