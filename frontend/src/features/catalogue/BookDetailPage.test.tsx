import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '../../api/types'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeBook, makeReservation } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import BookDetailPage from './BookDetailPage'

function renderDetail(role: Role, routes: Parameters<typeof mockApi>[0]) {
  tokenStore.set(makeToken())
  const api = mockApi({ 'GET /users/me': json(makeUser(role)), ...routes })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/catalogue/:id" element={<BookDetailPage />} />
        <Route path="/student/reservations" element={<p>reservations page</p>} />
      </Route>
    </Routes>,
    { route: '/catalogue/10' },
  )
  return api
}

describe('BookDetailPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows the book details and availability', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook({ pendingReservations: 2 })),
      'GET /reservations/mine': json(page([])),
    })

    expect(await screen.findByRole('heading', { name: 'Clean Code' })).toBeInTheDocument()
    expect(screen.getByText('Robert C. Martin')).toBeInTheDocument()
    expect(screen.getByText('9780132350884')).toBeInTheDocument()
    expect(screen.getByText('2 of 3 available')).toBeInTheDocument()
    expect(screen.getByText('2 students are waiting for this book.')).toBeInTheDocument()
  })

  it('lets a student reserve a copy that is on the shelf and says it is being held', async () => {
    const api = renderDetail('STUDENT', {
      'GET /books/10': json(makeBook()),
      'GET /reservations/mine': json(page([])),
      'POST /reservations': json(makeReservation({ status: 'READY', readyUntil: '2030-03-04T09:00:00Z', copyBarcode: 'CB-0001', queuePosition: null }), 201),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Reserve a copy' }))

    expect(await screen.findByText(/A copy is being held for you until/)).toBeInTheDocument()
    expect(api.callsTo('POST', '/reservations')[0].body).toEqual({ bookId: 10 })
  })

  it('offers to join the queue when every copy is out, and reports the position', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook({ availableCopies: 0, pendingReservations: 1 })),
      'GET /reservations/mine': json(page([])),
      'POST /reservations': json(makeReservation({ queuePosition: 2 }), 201),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Join the queue' }))

    expect(await screen.findByText('You joined the queue at position 2.')).toBeInTheDocument()
  })

  it('shows the server reason when a reservation is refused', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook()),
      'GET /reservations/mine': json(page([])),
      'POST /reservations': problem(422, 'Rule violated', 'You already have this book on loan.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Reserve a copy' }))

    expect(await screen.findByText('You already have this book on loan.')).toBeInTheDocument()
  })

  it('replaces the button with the reservation the student already has', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook({ availableCopies: 0 })),
      'GET /reservations/mine': json(page([makeReservation({ queuePosition: 3 })])),
    })

    expect(await screen.findByText('You are number 3 in the queue.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Reserve|Join/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Manage my reservations' })).toBeInTheDocument()
  })

  it('tells a student their held copy is ready to collect', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook()),
      'GET /reservations/mine': json(page([makeReservation({ status: 'READY', readyUntil: '2030-03-04T09:00:00Z', copyBarcode: 'CB-0001' })])),
    })

    expect(await screen.findByText(/Your copy is ready to collect from the desk until/)).toBeInTheDocument()
  })

  it('ignores reservations that are finished or for other books', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook()),
      'GET /reservations/mine': json(page([makeReservation({ status: 'CANCELLED' }), makeReservation({ id: 51, bookId: 99 })])),
    })

    expect(await screen.findByRole('button', { name: 'Reserve a copy' })).toBeInTheDocument()
  })

  it('does not offer reserving when the title has no copies', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': json(makeBook({ totalCopies: 0, availableCopies: 0 })),
      'GET /reservations/mine': json(page([])),
    })

    expect(await screen.findByText('There are no copies to reserve yet.')).toBeInTheDocument()
  })

  it.each(['LIBRARIAN', 'ADMIN'] as const)('does not offer reserving to a %s, and does not ask for their reservations', async (role) => {
    const api = renderDetail(role, { 'GET /books/10': json(makeBook()) })

    await screen.findByRole('heading', { name: 'Clean Code' })

    expect(screen.queryByRole('button', { name: /Reserve|Join/ })).not.toBeInTheDocument()
    expect(api.callsTo('GET', '/reservations/mine')).toHaveLength(0)
  })

  it('shows a clear error when the book does not exist', async () => {
    renderDetail('STUDENT', {
      'GET /books/10': problem(404, 'Not found', 'Book not found.'),
      'GET /reservations/mine': json(page([])),
    })

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Book not found.'))
  })
})
