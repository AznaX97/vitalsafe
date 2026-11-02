package com.vitalsafe.api.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import com.vitalsafe.api.common.FieldValidationException;
import com.vitalsafe.api.vitals.TimeBounds;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalReadingRepository;
import com.vitalsafe.api.vitals.VitalService;
import com.vitalsafe.api.vitals.dto.VitalResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FhirService {

    static final int MAX_IMPORT_CHARS = 100_000;
    static final int MAX_EXPORT_READINGS = 20_000;
    private static final int EXPORT_PAGE_SIZE = 500;

    private final FhirContext fhir;
    private final VitalReadingRepository readings;
    private final VitalFhirMapper mapper;
    private final VitalFhirImporter importer;
    private final VitalService vitals;

    public FhirService(FhirContext fhir, VitalReadingRepository readings, VitalFhirMapper mapper,
                       VitalFhirImporter importer, VitalService vitals) {
        this.fhir = fhir;
        this.readings = readings;
        this.mapper = mapper;
        this.importer = importer;
        this.vitals = vitals;
    }

    @Transactional(readOnly = true)
    public String observationJson(UUID userId, UUID id) {
        VitalReading reading = readings.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found"));
        return encode(mapper.toObservation(reading, "Patient/" + userId));
    }

    @Transactional(readOnly = true)
    public String exportJson(UUID userId, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException(Map.of("from", "from must not be after to"));
        }
        Instant start = from == null ? TimeBounds.EARLIEST : from;
        Instant end = to == null ? TimeBounds.LATEST : to;

        Page<VitalReading> page = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                userId, start, end, PageRequest.of(0, EXPORT_PAGE_SIZE));
        if (page.getTotalElements() > MAX_EXPORT_READINGS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413),
                    "Too many readings for one export. Use from and to to export in parts.");
        }
        List<VitalReading> all = new ArrayList<>(page.getContent());
        while (page.hasNext()) {
            page = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                    userId, start, end, page.nextPageable());
            all.addAll(page.getContent());
        }
        return encode(mapper.toBundle(userId, all));
    }

    /** Not transactional here: VitalService.create runs in its own transaction. */
    public VitalResponse importObservation(UUID userId, String body) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is empty");
        }
        if (body.length() > MAX_IMPORT_CHARS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413), "FHIR resource is too large");
        }

        IParser parser = fhir.newJsonParser();
        parser.setParserErrorHandler(new StrictErrorHandler());
        IBaseResource parsed;
        try {
            parsed = parser.parseResource(body);
        } catch (DataFormatException e) {
            // The parser's message can repeat parts of the input, so it is not passed on
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid FHIR R4 JSON resource");
        }
        if (!(parsed instanceof Observation observation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only Observation resources can be imported");
        }
        // The id and subject inside the file are ignored: the reading always belongs to the caller
        return vitals.create(userId, importer.toRequest(observation));
    }

    private String encode(Resource resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }
}