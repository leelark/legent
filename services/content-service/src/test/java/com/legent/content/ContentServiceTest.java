package com.legent.content;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "legent.internal.api-token=test_internal_api_token_32_chars_min"
})
@ActiveProfiles("test")
public class ContentServiceTest {
    // Base test class
}
