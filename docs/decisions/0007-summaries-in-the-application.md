# 0007: Summaries are calculated in the application

Status: accepted

## Context

Reading values are encrypted (see 0003), so the database cannot add them up or compare them.

## Decision

A summary loads one user's readings of one type for a time range, decrypts them, and calculates the
statistics in the application. At most 10,000 readings are processed per request
(`app.summary.max-readings`). Beyond that the request is refused with 413 and the user is asked for a
shorter range.

- Statistics: count, minimum, maximum and average (one decimal place, half up), the latest reading, and
  buckets by day, week (starting Monday) or month.
- Buckets use a time zone chosen by the caller (default UTC), because the same moment can fall on
  different dates in different places.
- Readings in more than one unit are refused. Averaging mg/dL together with mmol/L would be meaningless.
- If the user set a target range for that type and unit, the summary also counts how many readings were
  within, above or below it. This describes position against the user's own numbers only.

## Consequences

- No health value is ever readable by the database, even for aggregation.
- Cost grows with the number of readings in range, which is why the cap exists.
- A user with years of frequent readings must ask for shorter ranges.
- Pre-computed aggregates were rejected: they would have to be stored unencrypted or kept in sync.