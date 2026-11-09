import { fmt, scale, ticks, xFor, yDomain } from "../lib/chartMath";
import { TYPE_LABELS, type VitalType } from "../vitals/types";
import { describeBounds, formatPair } from "./describe";
import type { Bucket, BucketStat, Stat, TargetBreakdown } from "./types";

const WIDTH = 640;
const HEIGHT = 280;
const LEFT = 56;
const RIGHT = 16;
const TOP = 16;
const BOTTOM = 36;

interface Point {
  start: string;
  time: number;
  stat: Stat;
  count: number;
}

interface Series {
  name: string;
  css: string;
  points: Point[];
}

interface Band {
  low: number | null;
  high: number | null;
  css: string;
}

function pointsOf(buckets: BucketStat[], pick: (bucket: BucketStat) => Stat | null): Point[] {
  const points: Point[] = [];
  for (const bucket of buckets) {
    const stat = pick(bucket);
    if (stat) {
      points.push({ start: bucket.start, time: Date.parse(`${bucket.start}T00:00:00Z`), stat, count: bucket.count });
    }
  }
  return points;
}

function bucketLabel(start: string, bucket: Bucket): string {
  const date = new Date(`${start}T00:00:00`);
  return bucket === "MONTH"
    ? date.toLocaleDateString(undefined, { month: "short", year: "numeric" })
    : date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

interface Props {
  type: VitalType;
  unit: string | null;
  bucket: Bucket;
  buckets: BucketStat[];
  target: TargetBreakdown | null;
}

export default function TrendChart({ type, unit, bucket, buckets, target }: Props) {
  if (buckets.length === 0) {
    return null;
  }

  const pressure = type === "BLOOD_PRESSURE" && buckets.some((item) => item.secondary !== null);
  const series: Series[] = [
    { name: pressure ? "Systolic" : TYPE_LABELS[type], css: "series-a", points: pointsOf(buckets, (item) => item.primary) },
  ];
  if (pressure) {
    series.push({ name: "Diastolic", css: "series-b", points: pointsOf(buckets, (item) => item.secondary) });
  }

  const bands: Band[] = [{ low: target?.lowValue ?? null, high: target?.highValue ?? null, css: "band-a" }];
  if (pressure) {
    bands.push({ low: target?.lowSecondaryValue ?? null, high: target?.highSecondaryValue ?? null, css: "band-b" });
  }

  const values: number[] = [];
  for (const item of series) {
    for (const point of item.points) {
      values.push(point.stat.min, point.stat.max);
    }
  }
  for (const band of bands) {
    if (band.low !== null) {
      values.push(band.low);
    }
    if (band.high !== null) {
      values.push(band.high);
    }
  }

  const domain = yDomain(values);
  const y = (value: number) => scale(value, domain, HEIGHT - BOTTOM, TOP);
  const firstTime = series[0].points[0].time;
  const lastTime = series[0].points[series[0].points.length - 1].time;
  const x = (time: number) => xFor(time, firstTime, lastTime, LEFT + 8, WIDTH - RIGHT - 8);
  const unitText = unit ?? "";

  return (
    <div className="stack">
      <svg
        className="chart"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`Chart of the average ${TYPE_LABELS[type].toLowerCase()} per ${bucket.toLowerCase()}`}
      >
        {ticks(domain).map((value) => (
          <g key={value}>
            <line className="grid-line" x1={LEFT} x2={WIDTH - RIGHT} y1={y(value)} y2={y(value)} />
            <text className="axis-label" x={LEFT - 6} y={y(value) + 4} textAnchor="end">
              {fmt(value)}
            </text>
          </g>
        ))}

        {bands.map((band) => {
          if (band.low !== null && band.high !== null) {
            return (
              <rect
                key={band.css}
                className={band.css}
                x={LEFT}
                width={WIDTH - LEFT - RIGHT}
                y={y(band.high)}
                height={y(band.low) - y(band.high)}
              >
                <title>Range you set: {describeBounds(band.low, band.high, unitText)}</title>
              </rect>
            );
          }
          const single = band.low ?? band.high;
          if (single === null) {
            return null;
          }
          return (
            <line key={band.css} className="band-line" x1={LEFT} x2={WIDTH - RIGHT} y1={y(single)} y2={y(single)}>
              <title>Range you set: {describeBounds(band.low, band.high, unitText)}</title>
            </line>
          );
        })}

        {series.map((item) => (
          <g key={item.name} className={item.css}>
            <polyline
              className="line"
              fill="none"
              points={item.points.map((point) => `${x(point.time)},${y(point.stat.average)}`).join(" ")}
            />
            {item.points.map((point) => (
              <g key={point.start}>
                <line className="range" x1={x(point.time)} x2={x(point.time)} y1={y(point.stat.max)} y2={y(point.stat.min)} />
                <circle className="dot" cx={x(point.time)} cy={y(point.stat.average)} r="4">
                  <title>
                    {`${bucketLabel(point.start, bucket)}: average ${fmt(point.stat.average)} (lowest ${fmt(point.stat.min)}, highest ${fmt(point.stat.max)}, ${point.count} reading${point.count === 1 ? "" : "s"})`}
                  </title>
                </circle>
              </g>
            ))}
          </g>
        ))}

        <text className="axis-label" x={LEFT} y={HEIGHT - 10}>
          {bucketLabel(buckets[0].start, bucket)}
        </text>
        <text className="axis-label" x={WIDTH - RIGHT} y={HEIGHT - 10} textAnchor="end">
          {bucketLabel(buckets[buckets.length - 1].start, bucket)}
        </text>
      </svg>

      <ul className="legend">
        {series.map((item) => (
          <li key={item.name}>
            <span className={`swatch ${item.css}`} /> {item.name}
          </li>
        ))}
        {bands.some((band) => band.low !== null || band.high !== null) && <li>Shaded area or dashed line: the range you set</li>}
      </ul>

      <details>
        <summary>Show the numbers</summary>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Period starting</th>
                <th>Readings</th>
                <th>Average</th>
                <th>Lowest</th>
                <th>Highest</th>
              </tr>
            </thead>
            <tbody>
              {buckets.map((item) => (
                <tr key={item.start}>
                  <td>{bucketLabel(item.start, bucket)}</td>
                  <td>{item.count}</td>
                  <td>{formatPair(type, item.primary.average, item.secondary?.average ?? null, unit)}</td>
                  <td>{formatPair(type, item.primary.min, item.secondary?.min ?? null, unit)}</td>
                  <td>{formatPair(type, item.primary.max, item.secondary?.max ?? null, unit)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}