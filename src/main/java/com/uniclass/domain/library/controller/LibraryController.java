package com.uniclass.domain.library.controller;

import com.uniclass.domain.library.dto.CreateLibraryItemDto;
import com.uniclass.domain.library.dto.ImportFromLibraryDto;
import com.uniclass.domain.library.dto.LibraryItemResponseDto;
import com.uniclass.domain.library.dto.LibrarySummaryDto;
import com.uniclass.domain.library.entity.LibraryCategory;
import com.uniclass.domain.library.service.LibraryService;
import com.uniclass.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    /**
     * 교수 개인 자료 보관함 메인 화면
     */
    @GetMapping("/library")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String libraryIndex(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(value = "category", required = false) LibraryCategory category,
                               @RequestParam(value = "keyword", required = false) String keyword,
                               Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        List<LibraryItemResponseDto> items = libraryService.getMyLibraryItems(userDetails.getId(), category, keyword);
        LibrarySummaryDto summary = libraryService.getSummary(userDetails.getId());

        model.addAttribute("items", items);
        model.addAttribute("summary", summary);
        model.addAttribute("currentCategory", category);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("categories", LibraryCategory.values());
        model.addAttribute("uploadForm", new CreateLibraryItemDto());

        return "library/index";
    }

    /**
     * 보관함 파일 업로드
     */
    @PostMapping("/library/upload")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String uploadItem(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @Valid @ModelAttribute("uploadForm") CreateLibraryItemDto dto,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("libraryErrorMessage", errorMsg != null ? errorMsg : "입력값이 올바르지 않습니다.");
            return "redirect:/library";
        }

        try {
            libraryService.uploadItem(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("librarySuccessMessage", "자료가 보관함에 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            log.error("보관함 업로드 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("libraryErrorMessage", e.getMessage());
        }

        return "redirect:/library";
    }

    /**
     * 보관함 파일 다운로드
     */
    @GetMapping("/library/{id}/download")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public ResponseEntity<Resource> downloadItem(@PathVariable("id") Long id,
                                                 @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        LibraryService.DownloadResult result = libraryService.downloadItem(id, userDetails.getId());
        String encodedFilename = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (result.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(result.contentType());
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(result.resource());
    }

    /**
     * 보관함 파일 삭제
     */
    @PostMapping("/library/{id}/delete")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String deleteItem(@PathVariable("id") Long id,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            libraryService.deleteItem(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("librarySuccessMessage", "보관함 자료가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("보관함 삭제 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("libraryErrorMessage", e.getMessage());
        }

        return "redirect:/library";
    }

    /**
     * [REST API] 수업 상세 페이지 모달 등에서 사용하는 보관함 목록 JSON 조회
     */
    @GetMapping("/api/library/items")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    @ResponseBody
    public ResponseEntity<List<LibraryItemResponseDto>> getLibraryItemsApi(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                           @RequestParam(value = "category", required = false) LibraryCategory category,
                                                                           @RequestParam(value = "keyword", required = false) String keyword) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        List<LibraryItemResponseDto> items = libraryService.getMyLibraryItems(userDetails.getId(), category, keyword);
        return ResponseEntity.ok(items);
    }

    /**
     * 수업 주차 섹션에 보관함 자료 가져오기 (Import)
     */
    @PostMapping("/classes/{classroomId}/materials/import-library")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String importToClassroom(@PathVariable("classroomId") Long classroomId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    @Valid @ModelAttribute ImportFromLibraryDto dto,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("materialErrorMessage", errorMsg != null ? errorMsg : "가져올 주차와 자료를 선택해 주세요.");
            return "redirect:/classes/" + classroomId + "?tab=materials";
        }

        try {
            libraryService.importToClassroom(classroomId, userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "보관함 자료를 성공적으로 수업 주차에 추가했습니다.");
        } catch (Exception e) {
            log.error("보관함 자료 수업 가져오기 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId + "?tab=materials";
    }
}
