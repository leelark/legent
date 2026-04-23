package com.legent.content.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentPublishedEvent {

    private String tenantId;
    private String templateId;
    private String templateName;
    private String versionNumber;
    private String publishedAt;
}
