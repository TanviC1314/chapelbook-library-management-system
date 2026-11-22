import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeFine } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import MyFinesPage from './MyFinesPage'

function renderPage(balance: unknown, fines: Parameters<typeof mockApi>[0][string]) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('STUDENT')),
    'GET /fines/mine/balance': balance instanceof Response ? balance : json(balance),
    'GET /fines/mine': fines,
  })
  renderApp(
    <Routes>
      <Route path="/student/fines" element={<MyFinesPage />} />
    </Routes>,
    { route: '/student/fines' },
  )
  return api
}

describe('MyFinesPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows what is owed and lists each fine with its status', async () => {
    renderPage(
      { unpaid: 1.5, borrowingBlockedAbove: 5, blocked: false },
      json(page([makeFine(), makeFine({ id: 81, bookTitle: 'Old paid book', amount: 2, status: 'PAID', paidAt: '2030-03-25T09:00:00Z' })])),
    )

    expect(await screen.findByLabelText(/You owe .*1[.,]50/)).toBeInTheDocument()
    expect(screen.getByText('Clean Code')).toBeInTheDocument()
    // The same words are also options in the filter, so look at the badges only.
    expect(screen.getByText('Unpaid', { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByText('Paid', { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByText(/paid on/)).toBeInTheDocument()
  })

  it('reassures a student who owes a little that they can still borrow', async () => {
    renderPage({ unpaid: 1.5, borrowingBlockedAbove: 5, blocked: false }, json(page([makeFine()])))

    expect(await screen.findByText(/You can still borrow books/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('warns clearly when unpaid fines block borrowing', async () => {
    renderPage({ unpaid: 7.25, borrowingBlockedAbove: 5, blocked: true }, json(page([makeFine({ amount: 7.25 })])))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot borrow new books until your unpaid fines are .*5[.,]00 or less/)
  })

  it('says there is nothing to pay when the balance is zero', async () => {
    renderPage({ unpaid: 0, borrowingBlockedAbove: 5, blocked: false }, json(page([])))

    expect(await screen.findByText('You have no unpaid fines.')).toBeInTheDocument()
    expect(await screen.findByText('You have no fines')).toBeInTheDocument()
  })

  it('filters by status', async () => {
    const api = renderPage({ unpaid: 0, borrowingBlockedAbove: 5, blocked: false }, json(page([makeFine()])))
    await screen.findByText('Clean Code')

    await userEvent.selectOptions(screen.getByLabelText('Show'), 'Paid')

    await vi.waitFor(() => {
      const last = new URL(String(api.fetchMock.mock.calls.at(-1)![0]), 'http://x')
      expect(last.searchParams.get('status')).toBe('PAID')
    })
  })

  it('shows an error if the balance cannot be loaded, without hiding the list', async () => {
    renderPage(problem(500, 'Server error', 'Something went wrong on our side.'), json(page([makeFine()])))

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
  })
})
