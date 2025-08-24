package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.service.MailService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/mail")
public class MailController {





    @RestController
    @RequestMapping("/dev")
    class DevMailController {
        private final MailService mail;
        DevMailController(MailService mail) { this.mail = mail; }

    }
}
