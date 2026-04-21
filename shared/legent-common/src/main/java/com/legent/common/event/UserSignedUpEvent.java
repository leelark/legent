package com.legent.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignedUpEvent {
    private String userId;
    private String email;
    private String companyName;
    private String slug;
}
