package com.vitalsafe.api.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

    // The context is expensive to create and safe to share. Parsers are cheap and are created per use.
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4Cached();
    }
}