package com.uniclass.domain.home.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Value("${uniclass.current-semester:2026-1학기}")
    private String currentSemester;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "UniClass");
        model.addAttribute("currentSemester", currentSemester);
        return "index";
    }
}
