import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { json, makeAuthResponse, makeUser, mockApi, problem, renderApp } from '../../test/utils'
import RegisterPage from './RegisterPage'

function renderRegister() {
  return renderApp(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/student" element={<p>student portal</p>} />
    </Routes>,
    { route: '/register' },
  )
}

async function fill(values: { name?: string; email?: string; studentNumber?: string; password?: string; confirm?: string }) {
  const v = { name: 'Ann Student', email: 'ann@example.com', studentNumber: '', password: 'correct-horse', confirm: 'correct-horse', ...values }
  await userEvent.type(await screen.findByLabelText('Full name'), v.name)
  await userEvent.type(screen.getByLabelText('Email'), v.email)
  if (v.studentNumber) await userEvent.type(screen.getByLabelText('Student number (optional)'), v.studentNumber)
  await userEvent.type(screen.getByLabelText('Password'), v.password)
  await userEvent.type(screen.getByLabelText('Confirm password'), v.confirm)
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('does not offer a role: anyone who registers is a student', async () => {
    renderRegister()

    await screen.findByLabelText('Full name')
    expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument()
  })

  it('registers a student, sends no role, and goes straight to the student portal', async () => {
    const api = mockApi({ 'POST /auth/register': json(makeAuthResponse(makeUser('STUDENT')), 201) })
    renderRegister()

    await fill({ studentNumber: 'S-1001' })

    expect(await screen.findByText('student portal')).toBeInTheDocument()
    expect(api.callsTo('POST', '/auth/register')[0].body).toEqual({
      fullName: 'Ann Student',
      email: 'ann@example.com',
      password: 'correct-horse',
      studentNumber: 'S-1001',
    })
    expect(tokenStore.get()).not.toBeNull()
  })

  it('leaves the student number out of the request when it is blank', async () => {
    const api = mockApi({ 'POST /auth/register': json(makeAuthResponse(makeUser('STUDENT')), 201) })
    renderRegister()

    await fill({})

    await screen.findByText('student portal')
    expect(api.callsTo('POST', '/auth/register')[0].body).not.toHaveProperty('studentNumber')
  })

  it('catches mismatched passwords before calling the server', async () => {
    const api = mockApi({})
    renderRegister()

    await fill({ confirm: 'something-different' })

    expect(await screen.findByText('The passwords do not match')).toBeInTheDocument()
    expect(api.fetchMock).not.toHaveBeenCalled()
  })

  it('shows the server message when the email is already registered', async () => {
    mockApi({ 'POST /auth/register': problem(409, 'Conflict', 'An account with this email already exists.') })
    renderRegister()

    await fill({})

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('An account with this email already exists.'))
    expect(tokenStore.get()).toBeNull()
  })

  it('puts the server field errors next to the fields', async () => {
    mockApi({
      'POST /auth/register': problem(400, 'Invalid request', 'Validation failed.', [{ field: 'email', message: 'must be a well-formed email address' }]),
    })
    renderRegister()

    await fill({})

    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument()
  })
})
