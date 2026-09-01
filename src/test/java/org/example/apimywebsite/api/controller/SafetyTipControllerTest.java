package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.dto.SafetyTipDTO;
import org.example.apimywebsite.service.SafetyTipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyTipControllerTest {

    @Mock
    private SafetyTipService safetyTipService;

    @Test
    void getRandomTip_returnsTheServiceTipWrappedInTheDto() {
        SafetyTipController controller = new SafetyTipController(safetyTipService);
        when(safetyTipService.getRandomTip()).thenReturn("Use a strong, unique password for each of your online accounts.");

        ResponseEntity<SafetyTipDTO> response = controller.getRandomTip();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Use a strong, unique password for each of your online accounts.", response.getBody().getTip());
    }
}
