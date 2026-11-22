import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeLoan } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import MyBorrowingsPage from './MyBorrowingsPage'

function renderPage(routes: Parameters<typeof mockApi>[0], route = '/student/borrowings') {
  tokenStore.set(makeToken())
  const api = mockApi({ 'GET /users/me': json(makeUser('STUDENT')), ...routes })
  renderApp(
    <Routes>
      <Route path="/student/borrowings" element={<MyBorrowingsPage />} />
    </Routes>,
    { route },
  )
  return api
}

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString()

describe('MyBorrowingsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows books on loan by default with how long is left', async () => {
    const api = renderPage({ 'GET /loans/mine': json(page([makeLoan({ dueAt: inDays(5) })])) })

    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
    expect(screen.getByText(/due in 5 days/)).toBeInTheDocument()
    expect(screen.getByText('On loan')).toBeInTheDocument()
    expect(new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes('/loans/mine'))![0]), 'http://x').searchParams.get('activeOnly')).toBe('true')
  })

  it('warns about an overdue book in red, explains what to do, and offers no renewal', async () => {
    renderPage({ 'GET /loans/mine': json(page([makeLoan({ status: 'OVERDUE', dueAt: inDays(-3) })])) })

    expect(await screen.findByText(/3 days overdue/)).toHaveClass('text-red-700')
    expect(screen.getByText(/Please return this book to the desk/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Renew/ })).not.toBeInTheDocument()
  })

  it('switches to the full history, which includes returned books and their fines', async () => {
    const api = renderPage({
      'GET /loans/mine': (url) =>
        url.searchParams.get('activeOnly') === 'true'
          ? json(page([]))
          : json(page([makeLoan({ status: 'RETURNED', returnedAt: '2030-03-10T09:00:00Z', fineAmount: 0.75 })])),
    })
    expect(await screen.findByText('You have no books on loan')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'All borrowings' }))

    expect(await screen.findByText(/Returned on/)).toBeInTheDocument()
    expect(screen.getByText(/Fine .*0[.,]75/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'All borrowings' })).toHaveAttribute('aria-selected', 'true')
    const last = new URL(String(api.fetchMock.mock.calls.at(-1)![0]), 'http://x')
    expect(last.searchParams.get('activeOnly')).toBe('false')
  })

  it('renews a loan and shows the new due date', async () => {
    let renewed = false
    const api = renderPage({
      'GET /loans/mine': () => json(page([makeLoan({ dueAt: inDays(renewed ? 19 : 5), renewalCount: renewed ? 1 : 0 })])),
      'POST /loans/70/renew': () => {
        renewed = true
        return json(makeLoan({ dueAt: inDays(19), renewalCount: 1 }))
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Renew Clean Code' }))

    expect(await screen.findByText(/Renewed\. The new due date is/)).toBeInTheDocument()
    expect(await screen.findByText(/due in 19 days/)).toBeInTheDocument()
    expect(screen.getByText(/renewed 1 time/)).toBeInTheDocument()
    expect(api.callsTo('POST', '/loans/70/renew')).toHaveLength(1)
  })

  it('shows the librarys reason when a renewal is refused', async () => {
    renderPage({
      'GET /loans/mine': json(page([makeLoan({ dueAt: inDays(5) })])),
      'POST /loans/70/renew': problem(422, 'Rule violated', 'Another student is waiting for this book, so it cannot be renewed.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Renew Clean Code' }))

    expect(await screen.findByText('Another student is waiting for this book, so it cannot be renewed.')).toBeInTheDocument()
  })

  it('has an empty state that points to the catalogue', async () => {
    renderPage({ 'GET /loans/mine': json(page([])) })

    expect(await screen.findByText('You have no books on loan')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse the catalogue' })).toHaveAttribute('href', '/catalogue')
  })

  it('shows an error with a retry', async () => {
    let calls = 0
    renderPage({ 'GET /loans/mine': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([makeLoan({ dueAt: inDays(5) })]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(screen.getByText('Clean Code')).toBeInTheDocument())
  })
})
