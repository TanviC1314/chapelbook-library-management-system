import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeCirculationStats, makeSeatStats } from '../../test/stats'
import { json, makeToken, makeUser, mockApi, problem, renderApp } from '../../test/utils'
import AdminReportsPage from './AdminReportsPage'

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('ADMIN')),
    'GET /statistics/circulation': json(makeCirculationStats()),
    'GET /statistics/seats': json(makeSeatStats()),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/admin/reports" element={<AdminReportsPage />} />
      </Route>
    </Routes>,
    { route: '/admin/reports' },
  )
  return api
}

const lastQuery = (api: ReturnType<typeof mockApi>, path: string) => {
  const calls = api.fetchMock.mock.calls.filter((c) => new URL(String(c[0]), 'http://x').pathname.endsWith(path))
  return new URL(String(calls.at(-1)![0]), 'http://x').searchParams
}

function card(label: string) {
  return screen.getByText(label).closest('div') as HTMLElement
}

describe('AdminReportsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('opens on the last 30 days and asks both reports for that period', async () => {
    const api = renderPage()

    await screen.findByText('Loans issued')

    const circ = lastQuery(api, '/statistics/circulation')
    const seats = lastQuery(api, '/statistics/seats')
    expect(circ.get('from')).toBe(seats.get('from'))
    expect(circ.get('to')).toBe(seats.get('to'))
    expect((Date.parse(circ.get('to')!) - Date.parse(circ.get('from')!)) / 86_400_000).toBe(29)
    expect(screen.getByLabelText('From')).toHaveValue(circ.get('from'))
  })

  it('shows the circulation totals', async () => {
    renderPage()

    expect(await screen.findByText('Loans issued')).toBeInTheDocument()
    expect(within(card('Loans issued')).getByText('5')).toBeInTheDocument()
    expect(within(card('Loans returned')).getByText('3')).toBeInTheDocument()
    expect(within(card('Returned late')).getByText('1')).toBeInTheDocument()
    expect(within(card('Different borrowers')).getByText('4')).toBeInTheDocument()
    expect(within(card('Fines raised')).getByText(/0[.,]75/)).toBeInTheDocument()
    expect(within(card('Fines raised')).getByText('1 fine')).toBeInTheDocument()
  })

  it('lists the most borrowed books and charts loans by category, with the numbers readable', async () => {
    renderPage()

    const books = (await screen.findByText('Most borrowed books')).closest('section')!
    const rows = within(books).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByText('Clean Code')).toBeInTheDocument()
    expect(within(rows[0]).getByText('3')).toBeInTheDocument()
    const categories = await screen.findByRole('table', { name: /loans in each category/ })
    expect(within(categories).getAllByRole('row').slice(1).map((r) => r.textContent)).toEqual(['Computing3', 'History2'])
  })

  it('charts loans per day including quiet days', async () => {
    renderPage()

    const daily = await screen.findByRole('table', { name: /books issued and returned each day/ })
    expect(within(daily).getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell').map((c) => c.textContent))).toEqual([
      ['2', '0'],
      ['0', '1'],
      ['3', '2'],
    ])
  })

  it('shows the seat totals, usage by zone, and the busiest hours', async () => {
    renderPage()

    expect(await screen.findByText('Seat bookings')).toBeInTheDocument()
    expect(within(card('Seat bookings')).getByText('6')).toBeInTheDocument()
    expect(within(card('Cancelled')).getByText('1')).toBeInTheDocument()
    expect(within(card('Hours booked')).getByText('11.5 h')).toBeInTheDocument()
    expect(within(card('Different students')).getByText('4')).toBeInTheDocument()

    const zones = screen.getByText('Use by zone').closest('section')!
    const quiet = within(zones).getByRole('row', { name: /Quiet/ })
    expect(within(quiet).getAllByRole('cell').map((c) => c.textContent)).toEqual(['6', '4', '8.5 h', '0.47'])

    const peak = await screen.findByRole('table', { name: /active in each hour of the day/ })
    const rows = within(peak).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(24)
    expect(within(rows[10]).getByRole('rowheader')).toHaveTextContent('10:00')
    expect(within(rows[10]).getByRole('cell')).toHaveTextContent('3')
    expect(within(rows[3]).getByRole('cell')).toHaveTextContent('0')
  })

  it('switches period with a preset and asks the server again', async () => {
    const api = renderPage()
    await screen.findByText('Loans issued')

    await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }))

    await waitFor(() => {
      const q = lastQuery(api, '/statistics/circulation')
      expect((Date.parse(q.get('to')!) - Date.parse(q.get('from')!)) / 86_400_000).toBe(6)
    })
  })

  it('uses the dates typed in', async () => {
    const api = renderPage()
    await screen.findByText('Loans issued')

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2030-01-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2030-01-31' } })

    await waitFor(() => expect(lastQuery(api, '/statistics/seats').get('from')).toBe('2030-01-01'))
    expect(lastQuery(api, '/statistics/seats').get('to')).toBe('2030-01-31')
  })

  it.each([
    ['2030-03-05', '2030-03-01', 'The end date must not be before the start date.'],
    ['2020-01-01', '2030-01-01', 'A report can cover at most 366 days.'],
  ])('refuses the range %s to %s without asking the server', async (from, to, message) => {
    const api = renderPage()
    await screen.findByText('Loans issued')
    const before = api.callsTo('GET', '/statistics/circulation').length

    fireEvent.change(screen.getByLabelText('From'), { target: { value: from } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: to } })

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText('Loans issued')).not.toBeInTheDocument()
    // every step on the way to a bad range was itself a bad range, so the server was never asked
    expect(api.callsTo('GET', '/statistics/circulation')).toHaveLength(before)
  })

  it('shows an empty state when nothing was lent', async () => {
    renderPage({
      'GET /statistics/circulation': json(makeCirculationStats({ topBooks: [], byCategory: [], totals: { loansIssued: 0, loansReturned: 0, returnedLate: 0, distinctBorrowers: 0, finesRaised: 0, finesAmount: 0 } })),
    })

    expect((await screen.findAllByText('No loans in this period')).length).toBe(2)
  })

  it('fails one report without hiding the other, and retries', async () => {
    let calls = 0
    renderPage({ 'GET /statistics/circulation': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(makeCirculationStats())) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(await screen.findByText('Seat bookings')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Loans issued')).toBeInTheDocument()
  })
})
