package com.tayotayo.backend;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class HealthController {
    @GetMapping("/")
    public RedirectView apiDocs() {
        return new RedirectView("/swagger-ui.html", true);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "tayotayo-backend");
    }
}
