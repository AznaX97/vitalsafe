package com.vitalsafe.api.vitals.csv;

import com.vitalsafe.api.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/vitals")
public class VitalCsvController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final VitalCsvService service;

    public VitalCsvController(VitalCsvService service) {
        this.service = service;
    }

    @PostMapping(value = "/import", consumes = "text/csv")
    public VitalCsvService.ImportResult importCsv(@AuthenticationPrincipal AuthPrincipal caller,
                                                  HttpServletRequest request) throws IOException {
        // Read at most one byte past the limit: memory use stays bounded however much is sent
        byte[] body = request.getInputStream().readNBytes(VitalCsvService.MAX_BYTES + 1);
        if (body.length > VitalCsvService.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413), "The CSV file is too large (512 KB maximum)");
        }
        return service.importCsv(caller.userId(), body);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal AuthPrincipal caller,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vitalsafe-readings.csv\"")
                .body(service.exportCsv(caller.userId(), from, to));
    }
}