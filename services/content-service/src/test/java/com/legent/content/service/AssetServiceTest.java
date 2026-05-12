package com.legent.content.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void sanitizeFileNameStripsPathAndUnsafeCharacters() {
        AssetService service = new AssetService(null, null);

        assertEquals("evil-script.png", service.sanitizeFileName("..\\..\\evil<script>.png"));
        assertEquals("asset", service.sanitizeFileName("\u0000///"));
    }

    @Test
    void uploadAssetRejectsDisallowedContentTypeBeforeStorageWrite() {
        AssetService service = new AssetService(null, null);
        ReflectionTestUtils.setField(service, "maxAssetSizeBytes", 1024L);
        ReflectionTestUtils.setField(service, "allowedAssetContentTypes", "image/png,text/plain");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/x-msdownload",
                new byte[] {1, 2, 3}
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.uploadAsset(file, java.util.Map.of())
        );
        assertTrue(ex.getMessage().contains("not allowed"));
    }
}
