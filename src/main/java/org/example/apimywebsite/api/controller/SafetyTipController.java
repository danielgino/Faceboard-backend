package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.dto.SafetyTipDTO;
import org.example.apimywebsite.service.SafetyTipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TIP-001: no Gemini, no external API - SafetyTipService serves one tip from its own in-memory
// curated pool. Falls under SecurityConfig's default `.anyRequest().authenticated()` like every
// other endpoint here that isn't explicitly listed as permitAll, so no security changes needed.
@RestController
@RequestMapping("/safety-tips")
public class SafetyTipController {

    private final SafetyTipService safetyTipService;

    public SafetyTipController(SafetyTipService safetyTipService) {
        this.safetyTipService = safetyTipService;
    }

    @GetMapping("/random")
    public ResponseEntity<SafetyTipDTO> getRandomTip() {
        String tip = safetyTipService.getRandomTip();
        return ResponseEntity.ok(new SafetyTipDTO(tip));
    }
}
