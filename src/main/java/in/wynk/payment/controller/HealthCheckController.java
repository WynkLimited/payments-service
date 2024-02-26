package in.wynk.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class HealthCheckController {
    @GetMapping("/v1/health/status")
    public ResponseEntity<String> checkStatus() {
        return ResponseEntity.ok("UP");
    }
}
