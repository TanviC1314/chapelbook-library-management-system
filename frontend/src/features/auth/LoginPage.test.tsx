import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { json, makeAuthResponse, makeUser, mockApi, problem, renderApp } from '../../test/utils'
import LoginPage from './LoginPage'

function renderLogin() {
  return renderApp(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/student" element={<p>student portal</p>} />
      <Route path="/librarian" element={<p>librarian portal</p>} />
      <Route path="/admin" element={<p>admin portal</p>} />
      <Route path="/catalogue" element={<p>catalogue page</p>} />
    </Routes>,
    { route: '/login' },
  )
}

async function fillAndSubmit() {
  await userEvent.type(await screen.findByLabelText('Email'), 'someone@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('asks for both fields without calling the server when they are empty', async () => {
    const api = mockApi({})
    renderLogin()

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(api.fetchMock).not.toHaveBeenCalled()
  })

  it('shows one clear message when the credentials are wrong and stores no token', async () => {
    mockApi({ 'POST /auth/login': problem(401, 'Unauthorized', 'Invalid email or password.') })
    renderLogin()

    await fillAndSubmit()

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password.'))
    expect(tokenStore.get()).toBeNull()
  })

  it.each([
    ['STUDENT', 'student portal'],
    ['LIBRARIAN', 'librarian portal'],
    ['ADMIN', 'admin portal'],
  ] as const)('sends a %s to their own portal and keeps the token', async (role, portal) => {
    const response = makeAuthResponse(makeUser(role))
    mockApi({ 'POST /auth/login': json(response) })
    renderLogin()

    await fillAndSubmit()

    expect(await screen.findByText(portal)).toBeInTheDocument()
    expect(tokenStore.get()).toBe(response.accessToken)
  })

  it('shows a readable message when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    renderLogin()

    await fillAndSubmit()

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Check your connection and try again.'))
  })
})
