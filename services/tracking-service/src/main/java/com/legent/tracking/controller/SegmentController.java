package com.legent.tracking.controller;

import com.legent.tracking.service.SegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/segment")
@RequiredArgsConstructor
public class SegmentController {
    private final SegmentService segmentService;

    @GetMapping
    public List<Map<String, Object>> getSegment(@RequestParam String field, @RequestParam String value) {
        return segmentService.getSegment(field, value);
    }
}
