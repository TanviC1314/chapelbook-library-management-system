import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

/** One consistent palette for every chart, with enough contrast between the series. */
export const COLORS = {
  brand: '#4338ca',
  green: '#059669',
  amber: '#d97706',
  rose: '#e11d48',
  sky: '#0284c7',
}

export interface Series {
  key: string
  label: string
  color: string
}

type Row = Record<string, string | number>

interface BaseProps {
  data: Row[]
  /** The key holding each row's label (the x axis). */
  xKey: string
  series: Series[]
  /** A sentence describing the chart for people who cannot see it. */
  ariaLabel: string
  /** Header for the label column of the hidden data table, e.g. "Day". */
  xHeader: string
}

/**
 * A chart plus the same numbers as a hidden table. The picture is for sighted people; the table is what a screen
 * reader (or a test) reads, so nobody is locked out of the data by a canvas.
 */
function Frame({ data, xKey, series, ariaLabel, xHeader, children }: BaseProps & { children: ReactNode }) {
  return (
    <div>
      <div className="h-64" role="img" aria-label={ariaLabel}>
        <ResponsiveContainer width="100%" height="100%">
          {children as React.ReactElement}
        </ResponsiveContainer>
      </div>
      {/* sr-only on a <table> itself is not clipped by browsers, so it widens the page: clip a wrapper instead */}
      <div className="sr-only">
      <table>
        <caption>{ariaLabel}</caption>
        <thead>
          <tr>
            <th scope="col">{xHeader}</th>
            {series.map((s) => (
              <th key={s.key} scope="col">
                {s.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={String(row[xKey])}>
              <th scope="row">{row[xKey]}</th>
              {series.map((s) => (
                <td key={s.key}>{row[s.key]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  )
}

const axis = { tickLine: false, axisLine: false, fontSize: 12 } as const

export function BarSeriesChart(props: BaseProps & { stacked?: boolean }) {
  const { data, xKey, series, stacked } = props
  return (
    <Frame {...props}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey={xKey} {...axis} minTickGap={16} />
        <YAxis {...axis} width={40} allowDecimals={false} />
        <Tooltip cursor={{ fill: '#f1f5f9' }} />
        {series.length > 1 && <Legend iconType="circle" />}
        {series.map((s) => (
          <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color} stackId={stacked ? 'a' : undefined} radius={stacked ? undefined : [4, 4, 0, 0]} />
        ))}
      </BarChart>
    </Frame>
  )
}

export function LineSeriesChart(props: BaseProps) {
  const { data, xKey, series } = props
  return (
    <Frame {...props}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey={xKey} {...axis} minTickGap={16} />
        <YAxis {...axis} width={40} allowDecimals={false} />
        <Tooltip />
        <Legend iconType="circle" />
        {series.map((s) => (
          <Line key={s.key} type="monotone" dataKey={s.key} name={s.label} stroke={s.color} strokeWidth={2} dot={false} />
        ))}
      </LineChart>
    </Frame>
  )
}
