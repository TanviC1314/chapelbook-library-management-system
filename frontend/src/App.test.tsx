import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { tokenStore } from './auth/tokenStore'
import type { Role } from './api/types'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from './test/utils'

/** The whole app with a stored session, as after a page reload. */
function renderSignedIn(role: Role, route: string) {
  tokenStore.set(makeToken())
  mockApi({
    'GET /users/me': json(makeUser(role)),
    // what the student dashboard asks for on arrival
    'GET /loans/mine': json(page([])),
    'GET /reservations/mine': json(page([])),
    'GET /fines/mine/balance': json({ unpaid: 0, borrowingBlockedAbove: 5, blocked: false }),
    'GET /seat-bookings/mine': json(page([])),
  })
  return renderApp(<App />, { route })
}

describe('App routing and role access', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('sends a visitor who is not signed in to the login page, whatever they asked for', async () => {
    mockApi({})
    renderApp(<App />, { route: '/librarian/books' })

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('restores a stored session and sends "/" to the right portal', async () => {
    renderSignedIn('STUDENT', '/')

    expect(await screen.findByRole('heading', { name: 'Welcome, Test student' })).toBeInTheDocument()
    expect(await screen.findByText('You have no books on loan.')).toBeInTheDocument() // the student dashboard, not a placeholder
  })

  it('forgets a stored session the server no longer accepts', async () => {
    tokenStore.set(makeToken())
    mockApi({ 'GET /users/me': problem(401, 'Unauthorized', 'Authentication is required to access this resource.') })
    renderApp(<App />, { route: '/' })

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(tokenStore.get()).toBeNull()
  })

  it('shows a student only student navigation', async () => {
    renderSignedIn('STUDENT', '/student')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(nav).toHaveTextContent('My borrowings')
    expect(nav).toHaveTextContent('Study seats')
    expect(nav).not.toHaveTextContent('Manage books')
    expect(nav).not.toHaveTextContent('Users')
  })

  it.each([
    ['STUDENT', '/librarian'],
    ['STUDENT', '/admin'],
    ['LIBRARIAN', '/admin'],
  ] as const)('shows a %s the "no access" page at %s', async (role, route) => {
    renderSignedIn(role, route)

    expect(await screen.findByRole('heading', { name: 'You do not have access to this page' })).toBeInTheDocument()
  })

  it.each([
    ['LIBRARIAN', '/librarian'],
    ['ADMIN', '/librarian'],
    ['ADMIN', '/admin'],
  ] as const)('lets a %s open %s', async (role, route) => {
    renderSignedIn(role, route)

    expect(await screen.findByRole('heading', { name: /^Welcome, / })).toBeInTheDocument()
  })

  it('keeps a signed-in user off the login page', async () => {
    renderSignedIn('LIBRARIAN', '/login')

    expect(await screen.findByText('Librarian portal')).toBeInTheDocument()
  })

  it('shows a not-found page for an unknown address', async () => {
    renderSignedIn('STUDENT', '/no/such/page')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
