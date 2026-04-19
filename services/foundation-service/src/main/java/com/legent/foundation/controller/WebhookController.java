package com.legent.foundation.controller;

import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookIntegrationRepository repo;

    @GetMapping
    public List<WebhookIntegration> list() {
        return repo.findAll();
    }

    @PostMapping
    public WebhookIntegration save(@RequestBody WebhookIntegration wh) {
        return repo.save(wh);
    }
}
