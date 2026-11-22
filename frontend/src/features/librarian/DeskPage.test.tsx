import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeLoan } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import DeskPage from './DeskPage'

const ann = makeUser('STUDENT', { id: 7, fullName: 'Ann Student', email: 'ann@example.com', studentNumber: 'S-1001' })

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('LIBRARIAN')),
    'GET /users': (url) => json(page(url.searchParams.get('q')?.toLowerCase().includes('ann') ? [ann] : [])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route path="/librarian/desk" element={<DeskPage />} />
    </Routes>,
    { route: '/librarian/desk' },
  )
  return api
}

async function chooseAnn() {
  await userEvent.type(await screen.findByLabelText('Student'), 'ann')
  await userEvent.click(await screen.findByRole('button', { name: 'Select Ann Student' }))
}

describe('DeskPage: issuing', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('finds a student by name, email or number and shows who was chosen', async () => {
    const api = renderPage()

    await chooseAnn()

    expect(screen.getByText('Ann Student')).toBeInTheDocument()
    expect(screen.getByText('ann@example.com · S-1001')).toBeInTheDocument()
    const search = new URL(String(api.fetchMock.mock.calls.find((c) => new URL(String(c[0]), 'http://x').pathname === '/api/v1/users')![0]), 'http://x').searchParams
    expect(search.get('role')).toBe('STUDENT')
    expect(search.get('active')).toBe('true')
    expect(search.get('q')).toBe('ann')
  })

  it('says so when no active student matches', async () => {
    renderPage()

    await userEvent.type(await screen.findByLabelText('Student'), 'nobody')

    expect(await screen.findByText(/No active student matches “nobody”/)).toBeInTheDocument()
  })

  it('does not search on a single character', async () => {
    const api = renderPage()

    await userEvent.type(await screen.findByLabelText('Student'), 'a')
    await new Promise((r) => setTimeout(r, 450))

    expect(api.callsTo('GET', '/users')).toHaveLength(0)
  })

  it('lends the book to the chosen student and confirms with the due date', async () => {
    const api = renderPage({ 'POST /loans': json(makeLoan({ userName: 'Ann Student', bookTitle: 'Clean Code', copyBarcode: 'CB-0001', dueAt: '2030-03-15T09:00:00Z' }), 201) })
    await chooseAnn()

    await userEvent.type(screen.getByLabelText('Barcode of the book to lend'), 'CB-0001{Enter}')

    expect(await screen.findByText(/Lent “Clean Code” \(copy CB-0001\) to Ann Student\. Due back on/)).toBeInTheDocument()
    expect(api.callsTo('POST', '/loans')[0].body).toEqual({ userId: 7, copyBarcode: 'CB-0001' })
    // ready for the next book: the barcode box is empty and focused, the student stays selected
    await waitFor(() => expect(screen.getByLabelText('Barcode of the book to lend')).toHaveValue(''))
    expect(screen.getByLabelText('Barcode of the book to lend')).toHaveFocus()
    expect(screen.getByText('Ann Student')).toBeInTheDocument()
  })

  it('needs a student and a barcode before asking the server', async () => {
    const api = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Issue book' }))
    expect(await screen.findByText('Choose the student first.')).toBeInTheDocument()

    await chooseAnn()
    await userEvent.click(screen.getByRole('button', { name: 'Issue book' }))
    expect(await screen.findByText('Scan or type the barcode of the book.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/loans')).toHaveLength(0)
  })

  it('shows the library rule that stopped the loan and keeps the barcode to correct', async () => {
    renderPage({ 'POST /loans': problem(422, 'Rule violated', 'This copy is being held for another student.') })
    await chooseAnn()

    await userEvent.type(screen.getByLabelText('Barcode of the book to lend'), 'CB-0002{Enter}')

    expect(await screen.findByText('This copy is being held for another student.')).toBeInTheDocument()
    expect(screen.getByLabelText('Barcode of the book to lend')).toHaveValue('CB-0002')
  })

  it('lets the librarian switch to a different student', async () => {
    renderPage()
    await chooseAnn()

    await userEvent.click(screen.getByRole('button', { name: 'Change student' }))

    expect(await screen.findByLabelText('Student')).toBeInTheDocument()
  })
})

describe('DeskPage: returning', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  async function giveBack(barcode: string) {
    await userEvent.type(await screen.findByLabelText('Barcode of the book being returned'), `${barcode}{Enter}`)
  }

  it('takes a book back on time and says there is no fine', async () => {
    const api = renderPage({ 'POST /loans/return': json(makeLoan({ status: 'RETURNED', returnedAt: '2030-03-10T09:00:00Z', fineAmount: null })) })

    await giveBack('CB-0001')

    expect(await screen.findByText(/“Clean Code” \(copy CB-0001\) returned by Test student\./)).toBeInTheDocument()
    expect(screen.getByText('Returned on time: no fine.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/loans/return')[0].body).toEqual({ copyBarcode: 'CB-0001' })
  })

  it('warns about the fine when the book was late', async () => {
    renderPage({ 'POST /loans/return': json(makeLoan({ status: 'RETURNED', returnedAt: '2030-03-20T09:00:00Z', fineAmount: 1.25 })) })

    await giveBack('CB-0001')

    expect(await screen.findByText(/This book was late\. A fine of .*1[.,]25 has been added to Test student/)).toBeInTheDocument()
  })

  it('shows the reason when the copy is not on loan', async () => {
    renderPage({ 'POST /loans/return': problem(409, 'Conflict', 'This copy is not on loan.') })

    await giveBack('CB-9999')

    expect(await screen.findByText('This copy is not on loan.')).toBeInTheDocument()
  })

  it('needs a barcode', async () => {
    const api = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Return book' }))

    expect(await screen.findByText('Scan or type the barcode of the book.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/loans/return')).toHaveLength(0)
  })

  it('clears the barcode and refocuses it, ready for the next book', async () => {
    renderPage({ 'POST /loans/return': json(makeLoan({ status: 'RETURNED', returnedAt: '2030-03-10T09:00:00Z' })) })

    await giveBack('CB-0001')

    await screen.findByText(/returned by/)
    const input = screen.getByLabelText('Barcode of the book being returned')
    expect(input).toHaveValue('')
    expect(input).toHaveFocus()
  })
})
