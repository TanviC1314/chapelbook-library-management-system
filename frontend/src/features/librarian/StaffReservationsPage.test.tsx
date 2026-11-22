import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeReservation } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import StaffReservationsPage from './StaffReservationsPage'

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({ 'GET /users/me': json(makeUser('LIBRARIAN')), ...routes })
  renderApp(
    <Routes>
      <Route path="/librarian/reservations" element={<StaffReservationsPage />} />
    </Routes>,
    { route: '/librarian/reservations' },
  )
  return api
}

describe('StaffReservationsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows who reserved what, and where each one stands', async () => {
    renderPage({
      'GET /reservations': json(
        page([
          makeReservation({ id: 1, userName: 'Ann', bookTitle: 'Held book', status: 'READY', readyUntil: '2030-03-04T09:00:00Z', copyBarcode: 'CB-7', queuePosition: null }),
          makeReservation({ id: 2, userName: 'Bob', bookTitle: 'Queued book', status: 'PENDING', queuePosition: 3 }),
        ]),
      ),
    })

    expect(await screen.findByText(/Ann · Copy CB-7 held until/)).toBeInTheDocument()
    expect(screen.getByText('Bob · Number 3 in the queue')).toBeInTheDocument()
  })

  it('filters by status through the server', async () => {
    const api = renderPage({ 'GET /reservations': json(page([makeReservation()])) })
    await screen.findByText('Clean Code')

    await userEvent.selectOptions(screen.getByLabelText('Show'), 'Ready to collect')

    await vi.waitFor(() => expect(new URL(String(api.fetchMock.mock.calls.at(-1)![0]), 'http://x').searchParams.get('status')).toBe('READY'))
  })

  it('cancels any active reservation after confirmation and explains what happens to a held copy', async () => {
    const api = renderPage({
      'GET /reservations': json(page([makeReservation({ id: 5, userName: 'Ann', status: 'READY', readyUntil: '2030-03-04T09:00:00Z', copyBarcode: 'CB-7', queuePosition: null })])),
      'DELETE /reservations/5': json(makeReservation({ id: 5, status: 'CANCELLED', queuePosition: null })),
    })

    await userEvent.click(await screen.findByRole('button', { name: /Cancel Ann's reservation for Clean Code/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/goes to the next student in line/)).toBeInTheDocument()
    expect(api.callsTo('DELETE', '/reservations/5')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel reservation' }))

    expect(await screen.findByText(/Reservation cancelled\./)).toBeInTheDocument()
    expect(api.callsTo('DELETE', '/reservations/5')).toHaveLength(1)
  })

  it('offers no cancel button for a reservation that is already finished', async () => {
    renderPage({ 'GET /reservations': json(page([makeReservation({ status: 'FULFILLED', queuePosition: null })])) })

    await screen.findByText('Clean Code')

    expect(screen.queryByRole('button', { name: /Cancel/ })).not.toBeInTheDocument()
  })

  it('shows the server reason when cancelling fails', async () => {
    renderPage({
      'GET /reservations': json(page([makeReservation()])),
      'DELETE /reservations/50': problem(409, 'Conflict', 'This reservation is already cancelled.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: /Cancel Test student's reservation/ }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel reservation' }))

    expect(await screen.findByText('This reservation is already cancelled.')).toBeInTheDocument()
  })

  it('has empty and error states', async () => {
    let calls = 0
    renderPage({ 'GET /reservations': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('No reservations')).toBeInTheDocument()
  })
})
