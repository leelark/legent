package com.legent.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @NotBlank @Email
    private String email;
    
    @NotBlank
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    private String password;
    
    @NotBlank
    private String firstName;
    
    @NotBlank
    private String lastName;
    
    @NotBlank
    private String companyName;
    
    private String slug; // Optional, can be generated from companyName
}
