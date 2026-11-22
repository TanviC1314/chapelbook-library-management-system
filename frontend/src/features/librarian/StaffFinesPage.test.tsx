import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '../../api/types'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { makeFine } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import StaffFinesPage from './StaffFinesPage'

function renderPage(role: Role, routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({ 'GET /users/me': json(makeUser(role)), ...routes })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/librarian/fines" element={<StaffFinesPage />} />
      </Route>
    </Routes>,
    { route: '/librarian/fines' },
  )
  return api
}

const lastQuery = (api: ReturnType<typeof mockApi>) => new URL(String(api.fetchMock.mock.calls.at(-1)![0]), 'http://x').searchParams

describe('StaffFinesPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('opens on the unpaid fines, which is what staff come for', async () => {
    const api = renderPage('LIBRARIAN', { 'GET /fines': json(page([makeFine({ userName: 'Ann Student', amount: 2.5 })])) })

    expect(await screen.findByText('Ann Student')).toBeInTheDocument()
    expect(screen.getByText(/2[.,]50/)).toBeInTheDocument()
    expect(new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes('/fines'))![0]), 'http://x').searchParams.get('status')).toBe('UNPAID')
  })

  it('switches to paid, waived or all fines', async () => {
    const api = renderPage('LIBRARIAN', { 'GET /fines': json(page([makeFine()])) })
    await screen.findByText('Test student')

    await userEvent.selectOptions(screen.getByLabelText('Show'), 'All fines')
    await vi.waitFor(() => expect(lastQuery(api).get('status')).toBeNull())

    await userEvent.selectOptions(screen.getByLabelText('Show'), 'Paid')
    await vi.waitFor(() => expect(lastQuery(api).get('status')).toBe('PAID'))
  })

  it('records a payment only after confirmation', async () => {
    const api = renderPage('LIBRARIAN', {
      'GET /fines': json(page([makeFine({ id: 8, userName: 'Ann Student', amount: 2.5 })])),
      'POST /fines/8/pay': json(makeFine({ id: 8, userName: 'Ann Student', amount: 2.5, status: 'PAID', paidAt: '2030-03-21T09:00:00Z' })),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Record payment from Ann Student' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Confirm that Ann Student has paid .*2[.,]50 in full at the desk/)).toBeInTheDocument()
    expect(api.callsTo('POST', '/fines/8/pay')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }))

    expect(await screen.findByText(/Payment of .*2[.,]50 from Ann Student recorded\./)).toBeInTheDocument()
    expect(api.callsTo('POST', '/fines/8/pay')).toHaveLength(1)
  })

  it('offers payment but not waiving to a librarian', async () => {
    renderPage('LIBRARIAN', { 'GET /fines': json(page([makeFine()])) })

    await screen.findByText('Test student')

    expect(screen.getByRole('button', { name: /Record payment/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Waive/ })).not.toBeInTheDocument()
  })

  it('lets an administrator waive a fine, warning that it cannot be undone', async () => {
    const api = renderPage('ADMIN', {
      'GET /fines': json(page([makeFine({ id: 9, userName: 'Bob Student' })])),
      'POST /fines/9/waive': json(makeFine({ id: 9, userName: 'Bob Student', status: 'WAIVED' })),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Waive the fine for Bob Student' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/This cannot be undone/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive fine' }))

    expect(await screen.findByText('The fine for Bob Student was waived.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/fines/9/waive')).toHaveLength(1)
  })

  it('offers no actions on a fine that is already settled', async () => {
    renderPage('ADMIN', { 'GET /fines': json(page([makeFine({ status: 'PAID', paidAt: '2030-03-21T09:00:00Z' })])) })

    await screen.findByText('Test student')

    expect(screen.queryByRole('button', { name: /Record payment|Waive/ })).not.toBeInTheDocument()
    expect(screen.getByText(/paid /)).toBeInTheDocument()
  })

  it('shows the reason when two librarians record the same payment', async () => {
    renderPage('LIBRARIAN', {
      'GET /fines': json(page([makeFine({ id: 8 })])),
      'POST /fines/8/pay': problem(409, 'Conflict', 'This fine is already paid and cannot be paid.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: /Record payment from/ }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Record payment' }))

    expect(await screen.findByText('This fine is already paid and cannot be paid.')).toBeInTheDocument()
  })

  it('has a reassuring empty state and an error state', async () => {
    let calls = 0
    renderPage('LIBRARIAN', { 'GET /fines': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('No unpaid fines')).toBeInTheDocument()
    expect(screen.getByText('Everyone is up to date.')).toBeInTheDocument()
  })
})
