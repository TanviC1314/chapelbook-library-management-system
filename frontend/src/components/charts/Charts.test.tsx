import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { BarSeriesChart, COLORS, LineSeriesChart } from './Charts'

const data = [
  { day: '1 Mar', issued: 4, returned: 2 },
  { day: '2 Mar', issued: 0, returned: 3 },
]
const series = [
  { key: 'issued', label: 'Issued', color: COLORS.brand },
  { key: 'returned', label: 'Returned', color: COLORS.green },
]

describe('charts', () => {
  it.each([
    ['bar', <BarSeriesChart key="b" data={data} xKey="day" series={series} xHeader="Day" ariaLabel="Loans issued and returned each day" />],
    ['line', <LineSeriesChart key="l" data={data} xKey="day" series={series} xHeader="Day" ariaLabel="Loans issued and returned each day" />],
  ])('%s chart is labelled and carries every number in a table for screen readers', (_kind, chart) => {
    render(chart)

    expect(screen.getByRole('img', { name: 'Loans issued and returned each day' })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Loans issued and returned each day' })
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Day', 'Issued', 'Returned'])
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((r) => within(r).getAllByRole('cell').map((c) => c.textContent))).toEqual([
      ['4', '2'],
      ['0', '3'],
    ])
    expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent('1 Mar')
  })

  it('shows a zero as a zero, not as a missing value', () => {
    render(<BarSeriesChart data={data} xKey="day" series={series} xHeader="Day" ariaLabel="x" />)

    expect(within(screen.getByRole('table')).getAllByText('0')).toHaveLength(1)
  })
})
