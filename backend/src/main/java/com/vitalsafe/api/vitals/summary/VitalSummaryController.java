package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.security.AuthPrincipal;
import com.vitalsafe.api.vitals.VitalType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/vitals")
public class VitalSummaryController {

    private final VitalSummaryService service;

    public VitalSummaryController(VitalSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthPrincipal caller,
                                   @RequestParam(required = false) VitalType type,
                                   @RequestParam(required = false) String unit,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(defaultValue = "UTC") String zone,
                                   @RequestParam(defaultValue = "DAY") SummaryCalculator.Bucket bucket) {
        return service.summarize(caller.userId(), type, unit, from, to, zone, bucket);
    }
}