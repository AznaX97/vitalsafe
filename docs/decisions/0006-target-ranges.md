# 0006: Target ranges are chosen by the user

Status: accepted

## Decision

A user can set a range (a low bound, a high bound, or both) for each kind of reading and unit. Blood pressure
can have bounds for both the systolic and diastolic parts. Ranges are encrypted like readings, and each bound
is checked against the same plausibility limits as a reading.

## Why VitalSafe never decides what is healthy

Software that interprets health data can count as a medical device in some places. VitalSafe records, charts
and counts. It only ever says whether a reading is within, above or below **the range the user set**. The
user is expected to agree those numbers with a health professional.

## Consequences

- There are no built-in or suggested ranges.
- A range with no bounds is refused, in the API and by the database.
- Only one range exists per user, type and unit. Glucose can have one in mg/dL and one in mmol/L.