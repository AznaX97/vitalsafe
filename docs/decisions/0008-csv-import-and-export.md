# 0008: CSV import and export

Status: accepted

## Format

Header row required, comma separated, UTF-8 (a byte-order mark is allowed). Columns may be in any order and
the names are not case-sensitive: `type`, `value`, `secondaryValue`, `unit`, `measuredAt`, `note`. The columns
`type`, `value` and `measuredAt` are required. Times are ISO-8601 with a zone, such as `2026-01-15T08:00:00Z`.

## Rules

- At most 512 KB and 1,000 data rows per file. The body is read with a byte cap, so a large upload cannot
  use unbounded memory.
- Every row goes through the same plausibility rules as the normal API.
- All or nothing: if any row is not acceptable, nothing is imported and up to 50 bad rows are listed by row
  number. Row 1 is the header.
- Duplicates are skipped: a reading with the same type in the same second as an existing one (or an earlier
  row of the same file) is not added again. The response says how many were skipped.
- Error messages contain row numbers and fixed text, never the file's own content.

## Export and spreadsheet formulas

A note that starts with `=`, `+`, `-` or `@` (or a tab or carriage return) would be run as a formula by Excel
and similar programs. The export puts an apostrophe in front of such notes. Importing an export keeps that
apostrophe in the note. The other columns hold only enumerated names, numbers and times, so they are safe.
At most 20,000 readings are exported at once. Use `from` and `to` for more.

## Limits

- Two imports running at the same moment could still create a duplicate, because there is no database
  constraint on type and time.
- Excel needs a byte-order mark to open UTF-8 correctly, and the export does not write one.