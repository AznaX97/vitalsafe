# 0010: Charts are drawn as SVG, without a chart library

Status: accepted

## Decision

The trend chart is a small React component that draws SVG from the summary the backend calculated. It plots the
average per period as a line, the lowest and highest as a thin bar, and the range the person set as a shaded
band (or a dashed line when only one bound exists). Blood pressure draws two series.

## Why

- No extra dependency to keep patched.
- Every number is also shown in a table under the chart, so the data is available without seeing the picture.
- The wording stays under our control. The chart only ever says "the range you set". It never colours a value as
  healthy or unhealthy.

## Limits

- Only line and range charts exist. Zooming and panning do not.
- A chart with thousands of periods would be crowded. The summary period options keep it within about a year of days.
- The summary is calculated by the backend from at most 10,000 readings, so a very long period can be refused with
  a message asking for a shorter one.