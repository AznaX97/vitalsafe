package com.vitalsafe.api.fhir;

import com.vitalsafe.api.security.AuthPrincipal;
import com.vitalsafe.api.vitals.dto.VitalResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FhirController {

    private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json;charset=UTF-8");

    private final FhirService service;

    public FhirController(FhirService service) {
        this.service = service;
    }

    @GetMapping("/vitals/{id}/fhir")
    public ResponseEntity<String> observation(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable UUID id) {
        return ResponseEntity.ok().contentType(FHIR_JSON).body(service.observationJson(caller.userId(), id));
    }

    @GetMapping("/fhir/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal AuthPrincipal caller,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok()
                .contentType(FHIR_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vitalsafe-fhir-export.json\"")
                .body(service.exportJson(caller.userId(), from, to));
    }

    @PostMapping(value = "/vitals/fhir", consumes = {"application/fhir+json", "application/json"})
    public ResponseEntity<VitalResponse> importObservation(@AuthenticationPrincipal AuthPrincipal caller,
                                                           @RequestBody String body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importObservation(caller.userId(), body));
    }
}