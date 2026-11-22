import { screen, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeLoan, makeReservation, makeSeatBooking } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import StudentDashboard from './StudentDashboard'

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString()
const ok = { unpaid: 0, borrowingBlockedAbove: 5, blocked: false }

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('STUDENT', { fullName: 'Ann Student' })),
    'GET /loans/mine': json(page([])),
    'GET /reservations/mine': json(page([])),
    'GET /fines/mine/balance': json(ok),
    'GET /seat-bookings/mine': json(page([])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/student" element={<StudentDashboard />} />
      </Route>
    </Routes>,
    { route: '/student' },
  )
  return api
}

describe('StudentDashboard', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('greets the student and shows calm empty states when there is nothing going on', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Welcome, Ann Student' })).toBeInTheDocument()
    expect(await screen.findByText('You have no books on loan.')).toBeInTheDocument()
    expect(screen.getByText('You have no active reservations.')).toBeInTheDocument()
    expect(screen.getByText('No unpaid fines.')).toBeInTheDocument()
    expect(screen.getByText('You have no upcoming seat bookings.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows loans soonest-due first, with overdue ones flagged and a warning at the top', async () => {
    renderPage({
      'GET /loans/mine': json(
        page(
          [
            makeLoan({ id: 1, bookTitle: 'Later book', dueAt: inDays(9) }),
            makeLoan({ id: 2, bookTitle: 'Late book', status: 'OVERDUE', dueAt: inDays(-2) }),
            makeLoan({ id: 3, bookTitle: 'Soon book', dueAt: inDays(2) }),
          ],
          { totalElements: 3 },
        ),
      ),
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('You have 1 overdue book. Please return it to the desk')
    const titles = screen.getAllByText(/ book$/).map((el) => el.textContent)
    expect(titles.slice(0, 3)).toEqual(['Late book', 'Soon book', 'Later book'])
    expect(screen.getByText('2 days overdue')).toHaveClass('text-red-700')
  })

  it('announces a book that is ready to collect', async () => {
    renderPage({
      'GET /reservations/mine': json(page([makeReservation({ status: 'READY', bookTitle: 'Held book', readyUntil: inDays(2), copyBarcode: 'CB-1' }), makeReservation({ id: 51, status: 'PENDING' })])),
    })

    // (the loading spinner also has role=status, so find the banner by its words)
    expect(await screen.findByText(/“Held book” is ready to collect from the desk until/)).toBeInTheDocument()
    const card = screen.getByText('Reservations').closest('section')!
    expect(within(card).getByText('ready to collect', { exact: false })).toBeInTheDocument()
    expect(within(card).getByText('waiting in the queue', { exact: false })).toBeInTheDocument()
  })

  it('says how many are ready when there are several', async () => {
    renderPage({
      'GET /reservations/mine': json(page([makeReservation({ id: 1, status: 'READY', readyUntil: inDays(2) }), makeReservation({ id: 2, status: 'READY', readyUntil: inDays(2) })])),
    })

    expect(await screen.findByText('2 reserved books are ready to collect from the desk.')).toBeInTheDocument()
  })

  it('warns when unpaid fines block borrowing', async () => {
    renderPage({ 'GET /fines/mine/balance': json({ unpaid: 7.5, borrowingBlockedAbove: 5, blocked: true }) })

    expect(await screen.findByRole('alert')).toHaveTextContent(/you cannot borrow new books until they are .*5[.,]00 or less/)
    expect(screen.getByText('Unpaid. Pay at the library desk.')).toBeInTheDocument()
  })

  it('lists upcoming seat bookings', async () => {
    renderPage({ 'GET /seat-bookings/mine': json(page([makeSeatBooking({ seatLabel: 'Q-07' })])) })

    expect(await screen.findByText('Seat Q-07')).toBeInTheDocument()
  })

  it('keeps working when one section fails: only that card shows an error', async () => {
    renderPage({
      'GET /loans/mine': problem(500, 'Server error', 'Something went wrong on our side.'),
      'GET /fines/mine/balance': json({ unpaid: 2, borrowingBlockedAbove: 5, blocked: false }),
    })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(await screen.findByText('Unpaid. Pay at the library desk.')).toBeInTheDocument()
    expect(screen.getByText('You have no active reservations.')).toBeInTheDocument()
  })

  it('links to each area', async () => {
    renderPage()

    await screen.findByText('You have no books on loan.')

    expect(screen.getByRole('link', { name: 'See my borrowings' })).toHaveAttribute('href', '/student/borrowings')
    expect(screen.getByRole('link', { name: 'See my reservations' })).toHaveAttribute('href', '/student/reservations')
    expect(screen.getByRole('link', { name: 'See my fines' })).toHaveAttribute('href', '/student/fines')
    expect(screen.getByRole('link', { name: 'Book or manage seats' })).toHaveAttribute('href', '/student/seats')
  })
})
