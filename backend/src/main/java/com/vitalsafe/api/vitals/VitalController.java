package com.vitalsafe.api.vitals;

import com.vitalsafe.api.common.PageResponse;
import com.vitalsafe.api.security.AuthPrincipal;
import com.vitalsafe.api.vitals.dto.VitalRequest;
import com.vitalsafe.api.vitals.dto.VitalResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vitals")
public class VitalController {

    private final VitalService service;

    public VitalController(VitalService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VitalResponse create(@AuthenticationPrincipal AuthPrincipal caller,
                                @Valid @RequestBody VitalRequest request) {
        return service.create(caller.userId(), request);
    }

    @GetMapping
    public PageResponse<VitalResponse> list(@AuthenticationPrincipal AuthPrincipal caller,
                                            @RequestParam(required = false) VitalType type,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(caller.userId(), type, from, to, page, size);
    }

    @GetMapping("/{id}")
    public VitalResponse get(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable UUID id) {
        return service.get(caller.userId(), id);
    }

    @PutMapping("/{id}")
    public VitalResponse update(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable UUID id,
                                @Valid @RequestBody VitalRequest request) {
        return service.update(caller.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal caller, @PathVariable UUID id) {
        service.delete(caller.userId(), id);
    }
}