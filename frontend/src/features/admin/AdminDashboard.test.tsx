import { screen, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeOverview } from '../../test/overview'
import { makeCirculationStats, makeSeatStats } from '../../test/stats'
import { json, makeToken, makeUser, mockApi, problem, renderApp } from '../../test/utils'
import AdminDashboard from './AdminDashboard'

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('ADMIN', { fullName: 'Ada Admin' })),
    'GET /statistics/overview': json(makeOverview()),
    'GET /statistics/circulation': json(makeCirculationStats()),
    'GET /statistics/seats': json(makeSeatStats()),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/admin" element={<AdminDashboard />} />
      </Route>
    </Routes>,
    { route: '/admin' },
  )
  return api
}

function card(label: string) {
  return screen.getByText(label).closest('div, a') as HTMLElement
}

describe('AdminDashboard', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows account counts alongside the library numbers', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Welcome, Ada Admin' })).toBeInTheDocument()
    expect(await screen.findByText('Students')).toBeInTheDocument()
    expect(within(card('Students')).getByText('120')).toBeInTheDocument()
    expect(within(card('Librarians')).getByText('4')).toBeInTheDocument()
    expect(within(card('Administrators')).getByText('2')).toBeInTheDocument()
    expect(within(card('Deactivated accounts')).getByText('3')).toBeInTheDocument()
    expect(within(card('Books on loan')).getByText('230')).toBeInTheDocument()
    expect(within(card('Overdue')).getByText('14')).toBeInTheDocument()
  })

  it('links the account cards to the filtered user list', async () => {
    renderPage()
    await screen.findByText('Students')

    expect(card('Students')).toHaveAttribute('href', '/admin/users?role=STUDENT')
    expect(card('Librarians')).toHaveAttribute('href', '/admin/users?role=LIBRARIAN')
    expect(card('Deactivated accounts')).toHaveAttribute('href', '/admin/users?state=deactivated')
    expect(screen.getByRole('button', { name: 'Full reports' }).closest('a')).toHaveAttribute('href', '/admin/reports')
  })

  it('charts the last 30 days of loans and seat bookings, with every number available as a table', async () => {
    const api = renderPage()

    const loans = await screen.findByRole('table', { name: /books issued and returned each day over the last 30 days/ })
    const rows = within(loans).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getAllByRole('cell').map((c) => c.textContent)).toEqual(['2', '0'])
    expect(within(rows[2]).getAllByRole('cell').map((c) => c.textContent)).toEqual(['3', '2'])

    const seats = await screen.findByRole('table', { name: /seat bookings starting each day/ })
    expect(within(seats).getAllByRole('row').slice(1).map((r) => within(r).getByRole('cell').textContent)).toEqual(['1', '3', '2'])

    // both reports ask for the same 30-day window ending today
    const query = (path: string) => new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes(path))![0]), 'http://x').searchParams
    const circ = query('/statistics/circulation')
    expect(circ.get('to')).toBe(query('/statistics/seats').get('to'))
    expect((Date.parse(circ.get('to')!) - Date.parse(circ.get('from')!)) / 86_400_000).toBe(29)
  })

  it('keeps each part independent: a failing chart does not hide the numbers', async () => {
    renderPage({ 'GET /statistics/circulation': problem(500, 'Server error', 'Something went wrong on our side.') })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(await screen.findByText('Students')).toBeInTheDocument()
    expect(await screen.findByRole('table', { name: /seat bookings/ })).toBeInTheDocument()
  })

  it('shows an error instead of numbers if the overview cannot be loaded', async () => {
    renderPage({ 'GET /statistics/overview': problem(500, 'Server error', 'Something went wrong on our side.') })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(screen.queryByText('Students')).not.toBeInTheDocument()
  })
})
