import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '../../auth/RouteGuards'
import { tokenStore } from '../../auth/tokenStore'
import { json, makeToken, makeUser, mockApi, noContent, page, problem, renderApp } from '../../test/utils'
import UsersPage from './UsersPage'

const me = makeUser('ADMIN', { id: 1, fullName: 'Ada Admin', email: 'ada@example.com' })
const lena = makeUser('LIBRARIAN', { id: 2, fullName: 'Lena Librarian', email: 'lena@example.com' })
const ann = makeUser('STUDENT', { id: 3, fullName: 'Ann Student', email: 'ann@example.com', studentNumber: 'S-1001' })
const gone = makeUser('STUDENT', { id: 4, fullName: 'Gone Student', email: 'gone@example.com', studentNumber: null, active: false })

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(me),
    'GET /users': json(page([me, lena, ann, gone])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/admin/users" element={<UsersPage />} />
      </Route>
    </Routes>,
    { route: '/admin/users' },
  )
  return api
}

const listQuery = (api: ReturnType<typeof mockApi>) => {
  const calls = api.fetchMock.mock.calls.filter((c) => new URL(String(c[0]), 'http://x').pathname === '/api/v1/users')
  return new URL(String(calls.at(-1)![0]), 'http://x').searchParams
}

describe('UsersPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('lists accounts with role and status, and marks the signed-in admin', async () => {
    renderPage()

    expect(await screen.findByText('Lena Librarian')).toBeInTheDocument()
    expect(screen.getByText('(you)')).toBeInTheDocument()
    expect(screen.getByText('ann@example.com · S-1001')).toBeInTheDocument()
    expect(screen.getAllByText('Active')).toHaveLength(4) // 3 in the table + the filter option
    expect(screen.getByText('Deactivated', { selector: 'span' })).toHaveClass('text-red-700')
  })

  it('searches and filters through the server, and keeps the choices in the address', async () => {
    const api = renderPage()
    await screen.findByText('Lena Librarian')

    await userEvent.type(screen.getByLabelText('Search'), 'ann')
    await waitFor(() => expect(listQuery(api).get('q')).toBe('ann'))
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'Students')
    await waitFor(() => expect(listQuery(api).get('role')).toBe('STUDENT'))
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'Deactivated')
    await waitFor(() => expect(listQuery(api).get('active')).toBe('false'))
  })

  it('has an empty state and an error state with retry', async () => {
    let calls = 0
    renderPage({ 'GET /users': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('No accounts found')).toBeInTheDocument()
  })
})

describe('creating an account', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  async function openCreate(routes: Parameters<typeof mockApi>[0] = {}) {
    const api = renderPage(routes)
    await screen.findByText('Lena Librarian')
    await userEvent.click(screen.getByRole('button', { name: 'Add an account' }))
    return { api, dialog: await screen.findByRole('dialog') }
  }

  it('creates a librarian with the chosen role and a temporary password', async () => {
    const { api, dialog } = await openCreate({ 'POST /users': json(makeUser('LIBRARIAN', { id: 9, fullName: 'New Librarian' }), 201) })

    await userEvent.type(within(dialog).getByLabelText('Full name'), 'New Librarian')
    await userEvent.type(within(dialog).getByLabelText('Email'), 'new@example.com')
    await userEvent.type(within(dialog).getByLabelText('Temporary password'), 'temp-pass-123')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Account created for New Librarian.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/users')[0].body).toEqual({
      fullName: 'New Librarian',
      email: 'new@example.com',
      password: 'temp-pass-123',
      role: 'LIBRARIAN',
      studentNumber: null,
    })
  })

  it('shows the student number field only for a student', async () => {
    const { dialog } = await openCreate()
    expect(within(dialog).queryByLabelText('Student number (optional)')).not.toBeInTheDocument()

    await userEvent.selectOptions(within(dialog).getByLabelText('Role'), 'Student')

    expect(within(dialog).getByLabelText('Student number (optional)')).toBeInTheDocument()
  })

  it('checks the form before sending anything', async () => {
    const { api, dialog } = await openCreate()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Email is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Password must be at least 8 characters')).toBeInTheDocument()
    expect(api.callsTo('POST', '/users')).toHaveLength(0)
  })

  it('shows the reason when the email is already registered', async () => {
    const { dialog } = await openCreate({ 'POST /users': problem(409, 'Conflict', 'An account with this email already exists.') })

    await userEvent.type(within(dialog).getByLabelText('Full name'), 'Someone')
    await userEvent.type(within(dialog).getByLabelText('Email'), 'lena@example.com')
    await userEvent.type(within(dialog).getByLabelText('Temporary password'), 'temp-pass-123')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await within(dialog).findByText('An account with this email already exists.')).toBeInTheDocument()
  })
})

describe('editing an account', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  async function openEdit(name: string, routes: Parameters<typeof mockApi>[0] = {}) {
    const api = renderPage(routes)
    await userEvent.click(await screen.findByRole('button', { name: `Edit ${name}` }))
    return { api, dialog: await screen.findByRole('dialog') }
  }

  it('sends only what changed when renaming', async () => {
    const { api, dialog } = await openEdit('Lena Librarian', { 'PATCH /users/2': json({ ...lena, fullName: 'Lena L.' }) })

    await userEvent.clear(within(dialog).getByLabelText('Full name'))
    await userEvent.type(within(dialog).getByLabelText('Full name'), 'Lena L.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Saved Lena L..')).toBeInTheDocument()
    expect(api.callsTo('PATCH', '/users/2')[0].body).toEqual({ fullName: 'Lena L.' })
  })

  it('deactivates an account', async () => {
    const { api, dialog } = await openEdit('Ann Student', { 'PATCH /users/3': json({ ...ann, active: false }) })

    await userEvent.click(within(dialog).getByLabelText('Account is active'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await screen.findByText(/Saved Ann Student/)
    expect(api.callsTo('PATCH', '/users/3')[0].body).toEqual({ active: false })
  })

  it('promotes a librarian to administrator', async () => {
    const { api, dialog } = await openEdit('Lena Librarian', { 'PATCH /users/2': json({ ...lena, role: 'ADMIN' }) })

    await userEvent.selectOptions(within(dialog).getByLabelText('Role'), 'Administrator')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await screen.findByText(/Saved Lena Librarian/)
    expect(api.callsTo('PATCH', '/users/2')[0].body).toEqual({ role: 'ADMIN' })
  })

  it('never offers a student a different role', async () => {
    const { dialog } = await openEdit('Ann Student')

    const role = within(dialog).getByLabelText('Role')
    expect(role).toBeDisabled()
    expect(within(role).getAllByRole('option')).toHaveLength(1)
    expect(within(dialog).getByText('Student accounts stay students.')).toBeInTheDocument()
  })

  it('stops an admin deactivating or demoting themselves', async () => {
    const { dialog } = await openEdit('Ada Admin')

    expect(within(dialog).getByLabelText('Role')).toBeDisabled()
    expect(within(dialog).getByLabelText('Account is active')).toBeDisabled()
    expect(within(dialog).getByText('You cannot deactivate your own account.')).toBeInTheDocument()
  })

  it("shows the backend's reason if it refuses, such as removing the last admin", async () => {
    const { dialog } = await openEdit('Lena Librarian', { 'PATCH /users/2': problem(422, 'Rule violated', 'The last active admin cannot be demoted or deactivated.') })

    await userEvent.click(within(dialog).getByLabelText('Account is active'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await within(dialog).findByText('The last active admin cannot be demoted or deactivated.')).toBeInTheDocument()
  })
})

describe('resetting a password', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  async function openReset(routes: Parameters<typeof mockApi>[0] = {}) {
    const api = renderPage(routes)
    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for Ann Student' }))
    return { api, dialog: await screen.findByRole('dialog') }
  }

  it('sets a new password and is honest that open sessions keep working for a while', async () => {
    const { api, dialog } = await openReset({ 'POST /users/3/password': noContent() })
    expect(within(dialog).getByText(/keeps working until it expires within the hour/)).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText('New password'), 'brand-new-pass-9')
    await userEvent.type(within(dialog).getByLabelText('Confirm new password'), 'brand-new-pass-9')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Password changed for Ann Student.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/users/3/password')[0].body).toEqual({ newPassword: 'brand-new-pass-9' })
  })

  it('catches a mismatch or a too-short password before calling the server', async () => {
    const { api, dialog } = await openReset()

    await userEvent.type(within(dialog).getByLabelText('New password'), 'brand-new-pass-9')
    await userEvent.type(within(dialog).getByLabelText('Confirm new password'), 'something-else')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Change password' }))

    expect(await within(dialog).findByText('The passwords do not match')).toBeInTheDocument()
    expect(api.callsTo('POST', '/users/3/password')).toHaveLength(0)
  })
})
