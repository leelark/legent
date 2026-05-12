package com.legent.foundation.dto.performance;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ExtensionValidationRequest {
    private Map<String, Object> evidence;
}
