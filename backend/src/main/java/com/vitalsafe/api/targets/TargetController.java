package com.vitalsafe.api.targets;

import com.vitalsafe.api.security.AuthPrincipal;
import com.vitalsafe.api.targets.dto.TargetRequest;
import com.vitalsafe.api.targets.dto.TargetResponse;
import com.vitalsafe.api.vitals.VitalType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/targets")
public class TargetController {

    private final TargetService service;

    public TargetController(TargetService service) {
        this.service = service;
    }

    @GetMapping
    public List<TargetResponse> list(@AuthenticationPrincipal AuthPrincipal caller) {
        return service.list(caller.userId());
    }

    @PutMapping("/{type}")
    public TargetResponse set(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable VitalType type,
                              @Valid @RequestBody TargetRequest request) {
        return service.upsert(caller.userId(), type, request);
    }

    @DeleteMapping("/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable VitalType type,
                       @RequestParam(required = false) String unit) {
        service.delete(caller.userId(), type, unit);
    }
}