package com.legent.content.service;

import com.legent.content.dto.EmailStudioDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBuilderServiceTest {

    @Test
    void renderLayoutSanitizesBlocksAndAppliesVariables() {
        ContentBuilderService service = new ContentBuilderService(new EmailContentValidationService(), null);
        EmailStudioDto.BuilderBlock block = new EmailStudioDto.BuilderBlock();
        block.setId("hero");
        block.setBlockType("TEXT");
        block.setContent("<p>Hello {{firstName}}</p><script>alert(1)</script>");
        block.setStyles(Map.of("padding", 12, "backgroundColor", "#ffffff", "textColor", "#111827"));

        EmailStudioDto.BuilderLayoutRequest request = new EmailStudioDto.BuilderLayoutRequest();
        request.setSubject("Welcome {{firstName}}");
        request.setVariables(Map.of("firstName", "Ada"));
        request.setBlocks(List.of(block));

        EmailStudioDto.BuilderLayoutResponse response = service.renderLayout(request);

        assertThat(response.getSubject()).isEqualTo("Welcome Ada");
        assertThat(response.getHtmlContent()).contains("Hello Ada");
        assertThat(response.getHtmlContent()).doesNotContain("<script>");
        assertThat(response.getValidation().getStatus()).isEqualTo("PASS");
    }
}
