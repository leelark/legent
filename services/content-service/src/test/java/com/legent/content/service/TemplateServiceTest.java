package com.legent.content.service;

import com.legent.content.domain.EmailTemplate;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {
    @Test
    void testRenderTemplateWithPersonalization() {
        TemplateEngine engine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        engine.setTemplateResolver(resolver);
        
        TemplateService service = new TemplateService(null, engine, null);

        EmailTemplate template = new EmailTemplate();
        template.setHtmlContent("Hello, [[${name}]]!");
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        String rendered = service.renderTemplate(template, vars);
        assertEquals("Hello, Alice!", rendered.trim());
    }
}
