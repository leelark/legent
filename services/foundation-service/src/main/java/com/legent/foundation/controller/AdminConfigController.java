package com.legent.foundation.controller;

import com.legent.foundation.domain.AdminConfig;
import com.legent.foundation.repository.AdminConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
public class AdminConfigController {
    private final AdminConfigRepository repo;

    @GetMapping
    public List<AdminConfig> list() {
        return repo.findAll();
    }

    @PostMapping
    public AdminConfig save(@RequestBody AdminConfig config) {
        return repo.save(config);
    }
}
