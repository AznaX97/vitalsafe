# 0001: Store vitals in our own tables, exchange them as FHIR R4

Status: accepted

## Context

VitalSafe records vital signs. FHIR is the standard hospitals and health apps use to exchange such data.
A full FHIR server is a large system, and most of its features are not needed here.

## Decision

- Store readings in relational tables, with the values encrypted at rest.
- Use the HAPI FHIR library to build and parse FHIR R4 Observation resources for export, import and API output.
- Identify measurements with LOINC codes and units with UCUM.
- Show target ranges that the user sets. Never label a value as healthy or dangerous.

## Consequences

- The data model stays simple, and queries and charts are fast.
- Interoperability is limited to the resources we implement (Patient and Observation first).
- Adding more FHIR resources later means writing more mappings, not changing the database design.