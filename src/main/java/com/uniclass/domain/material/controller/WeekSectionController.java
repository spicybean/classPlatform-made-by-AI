package com.uniclass.domain.material.controller;

import com.uniclass.domain.material.dto.UpdateWeekSectionDto;
import com.uniclass.domain.material.entity.SectionType;
import com.uniclass.domain.material.service.MaterialService;
import com.uniclass.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/classes/{classroomId}/sections")
@RequiredArgsConstructor
public class WeekSectionController {

    private final MaterialService materialService;

    /**
     * 주차 정보 수정 (유형, 날짜, 공지사항, 제목, 숨김)
     */
    @PostMapping("/{sectionId}/update")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String updateWeekSection(@PathVariable("classroomId") Long classroomId,
                                    @PathVariable("sectionId") Long sectionId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    @ModelAttribute UpdateWeekSectionDto dto,
                                    RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            materialService.updateWeekSection(classroomId, sectionId, dto, userDetails.getId());
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "주차 설정이 저장되었습니다.");
        } catch (Exception e) {
            log.error("주차 설정 업데이트 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }
        return "redirect:/classes/" + classroomId + "?tab=materials";
    }

    /**
     * 신규 주차 / 보강 주차 추가
     */
    @PostMapping("/new")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String addCustomWeekSection(@PathVariable("classroomId") Long classroomId,
                                       @AuthenticationPrincipal CustomUserDetails userDetails,
                                       @RequestParam(value = "title", required = false) String title,
                                       @RequestParam(value = "sectionType", defaultValue = "MAKEUP") SectionType sectionType,
                                       RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            materialService.addCustomWeekSection(classroomId, title, sectionType, userDetails.getId());
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "새 주차가 성공적으로 추가되었습니다.");
        } catch (Exception e) {
            log.error("새 주차 추가 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }
        return "redirect:/classes/" + classroomId + "?tab=materials";
    }

    /**
     * 주차 섹션 삭제
     */
    @PostMapping("/{sectionId}/delete")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String deleteWeekSection(@PathVariable("classroomId") Long classroomId,
                                    @PathVariable("sectionId") Long sectionId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            materialService.deleteWeekSection(classroomId, sectionId, userDetails.getId());
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "주차가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("주차 삭제 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }
        return "redirect:/classes/" + classroomId + "?tab=materials";
    }

    /**
     * 원클릭 휴강 전환 / 강의 복구 토글
     */
    @PostMapping("/{sectionId}/toggle-holiday")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String toggleHoliday(@PathVariable("classroomId") Long classroomId,
                                @PathVariable("sectionId") Long sectionId,
                                @RequestParam(value = "reason", required = false, defaultValue = "공휴일 또는 학사 일정으로 인한 휴강 주차입니다.") String reason,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            materialService.toggleHoliday(classroomId, sectionId, reason, userDetails.getId());
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "주차 휴강/강의 상태가 전환되었습니다.");
        } catch (Exception e) {
            log.error("휴강 토글 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }
        return "redirect:/classes/" + classroomId + "?tab=materials";
    }
}
