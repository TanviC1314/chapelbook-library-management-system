import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { toLocalInputValue } from '../../lib/seatWindow'
import { makeSeat, makeSeatBooking } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import StudentSeatsPage from './StudentSeatsPage'

const seats = [
  makeSeat({ id: 1, label: 'Q-01', zone: 'Quiet', hasPower: true, available: true }),
  makeSeat({ id: 2, label: 'Q-02', zone: 'Quiet', hasPower: false, available: false }),
  makeSeat({ id: 3, label: 'G-01', zone: 'Group', hasPower: false, available: true }),
]

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('STUDENT')),
    'GET /seats': json(seats),
    'GET /seat-bookings/mine': json(page([])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route path="/student/seats" element={<StudentSeatsPage />} />
    </Routes>,
    { route: '/student/seats' },
  )
  return api
}

/** Tomorrow at the given local hour, in the form a datetime-local input takes. */
function tomorrowAt(hour: number): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  d.setHours(hour, 0, 0, 0)
  return toLocalInputValue(d)
}

function setWindow(start: string, end: string) {
  fireEvent.change(screen.getByLabelText('Start'), { target: { value: start } })
  fireEvent.change(screen.getByLabelText('End'), { target: { value: end } })
}

describe('StudentSeatsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('starts with a valid window and asks the backend which seats are free for it', async () => {
    const api = renderPage()

    expect(await screen.findByText('Seat Q-01')).toBeInTheDocument()
    const query = new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes('/seats'))![0]), 'http://x').searchParams
    expect(query.get('from')).toMatch(/Z$/)
    expect(Date.parse(query.get('to')!) - Date.parse(query.get('from')!)).toBe(2 * 3_600_000)
    expect(screen.getAllByText('Free')).toHaveLength(2)
    expect(screen.getAllByText('Taken')).toHaveLength(1)
    expect(screen.getAllByText('Power socket')).toHaveLength(1)
  })

  it('disables booking for a seat that is taken', async () => {
    renderPage()

    expect(await screen.findByRole('button', { name: 'Book seat Q-02' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Book seat Q-01' })).toBeEnabled()
  })

  it('books the seat for exactly the chosen window and confirms', async () => {
    const api = renderPage({
      'POST /seat-bookings': json(makeSeatBooking({ seatLabel: 'Q-01', startsAt: '2030-03-05T10:00:00Z', endsAt: '2030-03-05T12:00:00Z' }), 201),
    })
    await screen.findByText('Seat Q-01')
    setWindow(tomorrowAt(10), tomorrowAt(12))

    await userEvent.click(await screen.findByRole('button', { name: 'Book seat Q-01' }))

    expect(await screen.findByText(/Seat Q-01 booked/)).toBeInTheDocument()
    const sent = api.callsTo('POST', '/seat-bookings')[0].body as { seatId: number; startsAt: string; endsAt: string }
    expect(sent.seatId).toBe(1)
    expect(new Date(sent.startsAt).getTime()).toBe(new Date(tomorrowAt(10)).getTime())
    expect(new Date(sent.endsAt).getTime()).toBe(new Date(tomorrowAt(12)).getTime())
  })

  it('shows the backend reason when someone else got the seat first, and refreshes the picture', async () => {
    const api = renderPage({
      'POST /seat-bookings': problem(409, 'Conflict', 'This seat is already booked for part of that time.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Book seat Q-01' }))

    expect(await screen.findByText('This seat is already booked for part of that time.')).toBeInTheDocument()
    await waitFor(() => expect(api.callsTo('GET', '/seats').length).toBeGreaterThan(1)) // availability was reloaded
  })

  it.each([
    [() => tomorrowAt(12), () => tomorrowAt(10), 'The booking must end after it starts.'],
    [() => tomorrowAt(8), () => tomorrowAt(18), 'A booking can be at most 8 hours.'],
    [() => toLocalInputValue(new Date(Date.now() - 3 * 3_600_000)), () => toLocalInputValue(new Date(Date.now() - 3_600_000)), 'A booking cannot start in the past.'],
  ])('explains an unacceptable window instead of asking the server (%#)', async (from, to, message) => {
    const api = renderPage()
    await screen.findByText('Seat Q-01')
    const seatRequests = api.callsTo('GET', '/seats').length

    setWindow(from(), to())

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText('Seat Q-01')).not.toBeInTheDocument() // no stale seat list under an invalid window
    expect(api.callsTo('GET', '/seats')).toHaveLength(seatRequests)
  })

  it('filters by zone', async () => {
    renderPage()
    await screen.findByText('Seat Q-01')

    await userEvent.selectOptions(screen.getByLabelText('Zone'), 'Group')

    expect(screen.getByText('Seat G-01')).toBeInTheDocument()
    expect(screen.queryByText('Seat Q-01')).not.toBeInTheDocument()
  })

  it('lists upcoming bookings and cancels one after confirmation', async () => {
    let cancelled = false
    const api = renderPage({
      'GET /seat-bookings/mine': () => json(page(cancelled ? [] : [makeSeatBooking()])),
      'DELETE /seat-bookings/90': () => {
        cancelled = true
        return json(makeSeatBooking({ status: 'CANCELLED' }))
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel booking for seat Q-01' }))
    const dialog = await screen.findByRole('dialog')
    expect(api.callsTo('DELETE', '/seat-bookings/90')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel booking' }))

    expect(await screen.findByText('Booking cancelled.')).toBeInTheDocument()
    expect(await screen.findByText('You have no upcoming seat bookings.')).toBeInTheDocument()
  })

  it('says there are no upcoming bookings when there are none', async () => {
    renderPage()

    expect(await screen.findByText('You have no upcoming seat bookings.')).toBeInTheDocument()
  })

  it('shows an error with a retry when seats cannot be loaded', async () => {
    let calls = 0
    renderPage({ 'GET /seats': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(seats)) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Seat Q-01')).toBeInTheDocument()
  })
})
