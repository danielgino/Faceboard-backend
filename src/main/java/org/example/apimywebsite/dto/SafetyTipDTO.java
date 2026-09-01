package org.example.apimywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// TIP-001: response shape for GET /safety-tips/random. The frontend only ever reads `.tip`.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SafetyTipDTO {
    private String tip;
}
