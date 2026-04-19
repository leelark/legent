package com.legent.content.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssetServiceTest {
    @Test
    void testExtractFileName() {
        AssetService service = new AssetService(null, null);
        String url = "http://localhost:9000/legent-assets/image.png";
        String name = "image.png";
        String result = service.extractFileName(url, name);
        assertEquals("image.png", result);
    }
}
