import { screen, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeLoan } from '../../test/fixtures'
import { makeOverview } from '../../test/overview'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import LibrarianDashboard from './LibrarianDashboard'

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString()

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('LIBRARIAN', { fullName: 'Lena Librarian' })),
    'GET /statistics/overview': json(makeOverview()),
    'GET /loans': json(page([])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/librarian" element={<LibrarianDashboard />} />
      </Route>
    </Routes>,
    { route: '/librarian' },
  )
  return api
}

function card(label: string) {
  return screen.getByText(label).closest('div, a') as HTMLElement
}

describe('LibrarianDashboard', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows the live numbers from the overview', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Welcome, Lena Librarian' })).toBeInTheDocument()
    expect(await screen.findByText('Books on loan')).toBeInTheDocument()
    expect(within(card('Books on loan')).getByText('230')).toBeInTheDocument()
    expect(within(card('Books on loan')).getByText('21 issued, 17 returned today')).toBeInTheDocument()
    expect(within(card('Reservations')).getByText('15')).toBeInTheDocument()
    expect(within(card('Reservations')).getByText('6 ready to collect, 9 waiting')).toBeInTheDocument()
    expect(within(card('Copies available')).getByText('640')).toBeInTheDocument()
    expect(within(card('Copies available')).getByText('of 900 in circulation')).toBeInTheDocument()
    expect(within(card('Unpaid fines')).getByText(/42[.,]75/)).toBeInTheDocument()
    expect(within(card('Seats in service')).getByText('18 in use now')).toBeInTheDocument()
  })

  it('highlights overdue books in red and says when there are none', async () => {
    renderPage()
    expect(await screen.findByText('Chase these up')).toBeInTheDocument()
    expect(within(card('Overdue')).getByText('14')).toHaveClass('text-red-700')
  })

  it('stays calm when nothing is overdue', async () => {
    renderPage({ 'GET /statistics/overview': json(makeOverview({ circulation: { activeLoans: 5, overdueLoans: 0, issuedToday: 0, returnedToday: 0 } })) })

    expect(await screen.findByText('Nothing overdue')).toBeInTheDocument()
    expect(within(card('Overdue')).getByText('0')).not.toHaveClass('text-red-700')
    expect(await screen.findByText('No books are overdue.')).toBeInTheDocument()
  })

  it('lists the overdue loans with who has them and how late they are', async () => {
    const api = renderPage({
      'GET /loans': json(page([makeLoan({ id: 1, bookTitle: 'Late book', userName: 'Ann Student', copyBarcode: 'CB-9', status: 'OVERDUE', dueAt: inDays(-6) })], { totalElements: 14 })),
    })

    expect(await screen.findByText('Late book')).toBeInTheDocument()
    expect(screen.getByText(/Ann Student · copy CB-9/)).toBeInTheDocument()
    expect(screen.getByText('6 days overdue')).toBeInTheDocument()
    expect(screen.getByText('Showing the 1 longest overdue of 14.')).toBeInTheDocument()
    const request = new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes('/loans'))![0]), 'http://x')
    expect(request.searchParams.get('scope')).toBe('OVERDUE')
  })

  it('links the cards to the pages behind them', async () => {
    renderPage()
    await screen.findByText('Books on loan')

    expect(card('Books on loan')).toHaveAttribute('href', '/librarian/desk')
    expect(card('Reservations')).toHaveAttribute('href', '/librarian/reservations')
    expect(card('Unpaid fines')).toHaveAttribute('href', '/librarian/fines')
    expect(screen.getByRole('button', { name: 'Issue or return a book' }).closest('a')).toHaveAttribute('href', '/librarian/desk')
  })

  it('shows an error for the numbers without hiding the overdue list', async () => {
    renderPage({
      'GET /statistics/overview': problem(500, 'Server error', 'Something went wrong on our side.'),
      'GET /loans': json(page([makeLoan({ bookTitle: 'Late book', status: 'OVERDUE', dueAt: inDays(-2) })])),
    })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(await screen.findByText('Late book')).toBeInTheDocument()
  })
})
