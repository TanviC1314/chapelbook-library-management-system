import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeReservation } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import MyReservationsPage from './MyReservationsPage'

function renderPage(routes: Parameters<typeof mockApi>[0]) {
  tokenStore.set(makeToken())
  const api = mockApi({ 'GET /users/me': json(makeUser('STUDENT')), ...routes })
  renderApp(
    <Routes>
      <Route path="/student/reservations" element={<MyReservationsPage />} />
    </Routes>,
    { route: '/student/reservations' },
  )
  return api
}

describe('MyReservationsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('explains each reservation in plain words', async () => {
    renderPage({
      'GET /reservations/mine': json(
        page([
          makeReservation({ id: 1, bookTitle: 'Held book', status: 'READY', readyUntil: '2030-03-04T09:00:00Z', copyBarcode: 'CB-7', queuePosition: null }),
          makeReservation({ id: 2, bookTitle: 'Waiting book', status: 'PENDING', queuePosition: 4 }),
          makeReservation({ id: 3, bookTitle: 'Old book', status: 'EXPIRED', queuePosition: null }),
        ]),
      ),
    })

    expect(await screen.findByText(/A copy \(CB-7\) is held for you until/)).toBeInTheDocument()
    expect(screen.getByText('You are number 4 in the queue.')).toBeInTheDocument()
    expect(screen.getByText('The copy was not collected in time.')).toBeInTheDocument()
    expect(screen.getByText('Ready to collect')).toBeInTheDocument()
    expect(screen.getByText('In the queue')).toBeInTheDocument()
  })

  it('only offers cancelling for reservations that are still active', async () => {
    renderPage({
      'GET /reservations/mine': json(
        page([
          makeReservation({ id: 1, bookTitle: 'Active book', status: 'PENDING' }),
          makeReservation({ id: 2, bookTitle: 'Finished book', status: 'FULFILLED', queuePosition: null }),
        ]),
      ),
    })

    await screen.findByText('Active book')

    expect(screen.getByRole('button', { name: 'Cancel reservation for Active book' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel reservation for Finished book' })).not.toBeInTheDocument()
  })

  it('asks for confirmation, then cancels and refreshes the list', async () => {
    let cancelled = false
    const api = renderPage({
      'GET /reservations/mine': () => json(page(cancelled ? [makeReservation({ status: 'CANCELLED', queuePosition: null })] : [makeReservation()])),
      'DELETE /reservations/50': () => {
        cancelled = true
        return json(makeReservation({ status: 'CANCELLED', queuePosition: null }))
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel reservation for Clean Code' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/You will lose your place in the queue/)).toBeInTheDocument()
    expect(api.callsTo('DELETE', '/reservations/50')).toHaveLength(0) // nothing happens until they confirm
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel reservation' }))

    expect(await screen.findByText('Reservation cancelled.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('You cancelled this reservation.')).toBeInTheDocument()
  })

  it('does nothing when the student backs out of the confirmation', async () => {
    const api = renderPage({ 'GET /reservations/mine': json(page([makeReservation()])) })

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel reservation for Clean Code' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.callsTo('DELETE', '/reservations/50')).toHaveLength(0)
  })

  it('shows the server reason if cancelling fails', async () => {
    renderPage({
      'GET /reservations/mine': json(page([makeReservation()])),
      'DELETE /reservations/50': problem(409, 'Conflict', 'This reservation is already cancelled.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel reservation for Clean Code' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel reservation' }))

    expect(await screen.findByText('This reservation is already cancelled.')).toBeInTheDocument()
  })

  it('has a helpful empty state', async () => {
    renderPage({ 'GET /reservations/mine': json(page([])) })

    expect(await screen.findByText('You have no reservations')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse the catalogue' })).toHaveAttribute('href', '/catalogue')
  })

  it('shows an error with a retry', async () => {
    let calls = 0
    renderPage({ 'GET /reservations/mine': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([makeReservation()]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
  })
})
