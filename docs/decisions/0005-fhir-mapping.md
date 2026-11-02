# 0005: How readings map to FHIR R4

Status: accepted

## What is produced

Each reading becomes an Observation with status `final`, a category, a LOINC code, an effective time in
UTC with whole seconds, and a Quantity with UCUM units. The subject is `Patient/<user id>` for a single
Observation, and `urn:uuid:<user id>` inside an export Bundle. A Bundle is a `collection` containing one
Patient (an id only: no name, email or date of birth) followed by the Observations.

| Reading | Observation.code (LOINC) | Value |
|---|---|---|
| Blood pressure | 85354-9 panel | Components 8480-6 systolic and 8462-4 diastolic, in `mm[Hg]` |
| Heart rate | 8867-4 | `/min` |
| Body weight | 29463-7 | `kg` |
| Body temperature | 8310-5 | `Cel` |
| Oxygen saturation | 2708-6 and 59408-5 | `%` |
| Blood glucose | 2339-0 for `mg/dL`, 15074-8 for `mmol/L` | `mg/dL` or `mmol/L` |

## Import rules

Only Observations in status final, amended or corrected are accepted. The LOINC code must map to exactly
one reading type. Quantities must use UCUM, with no comparators such as `<`. The time must have seconds and
a time zone. Blood pressure needs both components. A glucose code must match its unit. The `id` and
`subject` in the file are ignored, and the reading always belongs to the caller. After conversion the
reading goes through the same plausibility rules as the normal API.

## Limits and honest caveats

- Output is **not validated against the FHIR vital-signs profiles**. It follows their shape, but a profile
  validator (HAPI has one) has not been run.
- Glucose is classed as `laboratory`, not `vital-signs`. That is a judgement call. Home glucose meters are
  often recorded either way, so check what your receiving system expects.
- Times are written to whole seconds, so milliseconds are dropped on export.
- Only Patient and Observation are produced. No Practitioner, Device or Encounter.
- Check the LOINC codes at loinc.org before relying on them outside this project.
- Import accepts at most 100,000 characters, and an export at most 20,000 readings (use `from` and `to`).