package com.uniclass.domain.home.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final String currentSemester;

    public HomeController(@Value("${uniclass.current-semester:2026-1학기}") String currentSemester) {
        this.currentSemester = currentSemester;
    }

    @GetMapping("/")
    public String index(@RequestParam(value = "logout", required = false) String logout, Model model) {
        model.addAttribute("appName", "UniClass");
        model.addAttribute("currentSemester", currentSemester);
        if (logout != null) {
            model.addAttribute("logoutMessage", "성공적으로 로그아웃되었습니다.");
        }
        return "index";
    }
}
