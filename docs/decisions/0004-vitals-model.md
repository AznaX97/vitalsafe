# 0004: Vitals data model, units and plausibility limits

Status: accepted

## Decision

Each reading has a type, a UCUM unit, a time, a value (plus a second value for blood pressure) and an
optional note. The values and note are encrypted (see 0003). The API takes UCUM unit codes.

| Type | LOINC | UCUM unit | Accepted range (typo guard) |
|---|---|---|---|
| Blood pressure | 85354-9 (panel), 8480-6 systolic, 8462-4 diastolic | `mm[Hg]` | systolic 50 to 300, diastolic 30 to 200, diastolic lower than systolic, whole numbers |
| Heart rate | 8867-4 | `/min` | 20 to 250, whole numbers |
| Body weight | 29463-7 | `kg` | 1 to 500 |
| Body temperature | 8310-5 | `Cel` | 30 to 45 |
| Oxygen saturation | 59408-5 (also 2708-6) | `%` | 50 to 100 |
| Blood glucose | 2339-0 in `mg/dL`, 15074-8 in `mmol/L` | `mg/dL` or `mmol/L`, always stated | 10 to 1000 mg/dL, 0.5 to 55 mmol/L |

Values may have at most two decimal places, and trailing zeros are removed before storing.

## The limits are not medical advice

They only reject numbers that cannot be real readings (for example 98.6 typed as degrees Celsius).
VitalSafe never calls a value healthy or unhealthy. Target ranges, when added, are set by the user.

## Why glucose needs a unit

mg/dL and mmol/L differ by a factor of about 18. Assuming one would store a dangerous mistake silently.

## Codes

The LOINC codes follow the FHIR vital signs profile. Check them against loinc.org (free account) before
relying on them for anything beyond this project.

## Consequences

- Only the units above are accepted for now. Pounds and degrees Fahrenheit are a later addition.
- Adding a type means: a migration for the type check, an enum value, a rule, a LOINC mapping and tests.