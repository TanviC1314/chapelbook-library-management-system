import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeBook, makeCopy } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, problem, renderApp } from '../../test/utils'
import CopiesPage from './CopiesPage'

function renderPage(copies: ReturnType<typeof makeCopy>[], routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('LIBRARIAN')),
    'GET /books/10': json(makeBook()),
    'GET /books/10/copies': json(copies),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route path="/librarian/books/:id" element={<CopiesPage />} />
      <Route path="/librarian/books" element={<p>all books</p>} />
    </Routes>,
    { route: '/librarian/books/10' },
  )
  return api
}

describe('CopiesPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('shows the title, its availability and every copy with its status and shelf', async () => {
    renderPage([makeCopy(), makeCopy({ id: 101, barcode: 'CB-0002', status: 'ON_LOAN', shelfLocation: null })])

    expect(await screen.findByRole('heading', { name: 'Clean Code' })).toBeInTheDocument()
    expect(screen.getByText('2 of 3 available')).toBeInTheDocument()
    expect(screen.getByText('CB-0001')).toBeInTheDocument()
    expect(screen.getByText('A1')).toBeInTheDocument()
    expect(screen.getByText('On loan: take it back at the issue and return desk.')).toBeInTheDocument()
  })

  it('only offers the status changes the backend allows for each status', async () => {
    renderPage([
      makeCopy({ id: 1, barcode: 'AV', status: 'AVAILABLE' }),
      makeCopy({ id: 2, barcode: 'LN', status: 'ON_LOAN' }),
      makeCopy({ id: 3, barcode: 'HD', status: 'HELD' }),
      makeCopy({ id: 4, barcode: 'LS', status: 'LOST' }),
      makeCopy({ id: 5, barcode: 'WD', status: 'WITHDRAWN' }),
    ])
    await screen.findByText('AV')

    expect(screen.getByRole('button', { name: 'Withdraw AV' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark AV as lost' })).toBeInTheDocument()
    for (const code of ['LN', 'HD']) {
      expect(screen.queryByRole('button', { name: new RegExp(`Withdraw ${code}|Mark ${code}|Restore ${code}`) })).not.toBeInTheDocument()
    }
    expect(screen.getByRole('button', { name: 'Restore LS to the shelf' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restore WD to the shelf' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Withdraw LS' })).not.toBeInTheDocument()
  })

  it('adds a copy', async () => {
    const api = renderPage([], { 'POST /books/10/copies': json(makeCopy({ id: 102, barcode: 'CB-NEW' }), 201) })

    await userEvent.click((await screen.findAllByRole('button', { name: 'Add a copy' }))[0])
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Barcode'), 'CB-NEW')
    await userEvent.type(within(dialog).getByLabelText('Shelf location (optional)'), 'B2')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add copy' }))

    expect(await screen.findByText('Copy CB-NEW added.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/books/10/copies')[0].body).toEqual({ barcode: 'CB-NEW', shelfLocation: 'B2' })
  })

  it('tells the librarian when a new copy went straight to a waiting student', async () => {
    renderPage([], { 'POST /books/10/copies': json(makeCopy({ barcode: 'CB-NEW', status: 'HELD' }), 201) })

    await userEvent.click((await screen.findAllByRole('button', { name: 'Add a copy' }))[0])
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Barcode'), 'CB-NEW')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add copy' }))

    expect(await screen.findByText('Copy CB-NEW added and held for the student waiting longest.')).toBeInTheDocument()
  })

  it('requires a barcode and shows the reason when it is already in use', async () => {
    const api = renderPage([makeCopy()], { 'POST /books/10/copies': problem(409, 'Conflict', 'A copy with barcode "CB-0001" already exists.') })

    await userEvent.click(await screen.findByRole('button', { name: 'Add a copy' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add copy' }))
    expect(await within(dialog).findByText('Barcode is required')).toBeInTheDocument()
    expect(api.callsTo('POST', '/books/10/copies')).toHaveLength(0)

    await userEvent.type(within(dialog).getByLabelText('Barcode'), 'CB-0001')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add copy' }))
    expect(await within(dialog).findByText('A copy with barcode "CB-0001" already exists.')).toBeInTheDocument()
  })

  it('edits only the shelf location, keeping the barcode fixed', async () => {
    const api = renderPage([makeCopy()], { 'PATCH /copies/100': json(makeCopy({ shelfLocation: 'Z9' })) })

    await userEvent.click(await screen.findByRole('button', { name: 'Edit shelf of CB-0001' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Barcode')).toBeDisabled()
    await userEvent.clear(within(dialog).getByLabelText('Shelf location (optional)'))
    await userEvent.type(within(dialog).getByLabelText('Shelf location (optional)'), 'Z9')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Shelf location saved.')).toBeInTheDocument()
    expect(api.callsTo('PATCH', '/copies/100')[0].body).toEqual({ shelfLocation: 'Z9' })
  })

  it('withdraws a copy only after explaining and confirming', async () => {
    const api = renderPage([makeCopy()], { 'PATCH /copies/100': json(makeCopy({ status: 'WITHDRAWN' })) })

    await userEvent.click(await screen.findByRole('button', { name: 'Withdraw CB-0001' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/taken out of circulation/)).toBeInTheDocument()
    expect(api.callsTo('PATCH', '/copies/100')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Withdraw copy' }))

    expect(await screen.findByText('Copy CB-0001 is now withdrawn.')).toBeInTheDocument()
    expect(api.callsTo('PATCH', '/copies/100')[0].body).toEqual({ status: 'WITHDRAWN' })
  })

  it('says a restored copy was passed to a waiting student', async () => {
    renderPage([makeCopy({ status: 'LOST' })], { 'PATCH /copies/100': json(makeCopy({ status: 'HELD' })) })

    await userEvent.click(await screen.findByRole('button', { name: 'Restore CB-0001 to the shelf' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Restore to shelf' }))

    expect(await screen.findByText('Restored. A student was waiting, so this copy is now held for them.')).toBeInTheDocument()
  })

  it('shows the backend reason if a change is refused and reloads the real state', async () => {
    const api = renderPage([makeCopy()], {
      'PATCH /copies/100': problem(422, 'Rule violated', 'A copy that is on loan cannot be changed here. Return it or cancel the reservation first.'),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Mark CB-0001 as lost' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Mark as lost' }))

    expect(await screen.findByText(/cannot be changed here/)).toBeInTheDocument()
    await waitFor(() => expect(api.callsTo('GET', '/books/10/copies').length).toBeGreaterThan(1))
  })

  it('has an empty state and an error state', async () => {
    renderPage([])
    expect(await screen.findByText('This title has no copies yet')).toBeInTheDocument()
  })

  it('shows a clear error when the book does not exist', async () => {
    renderPage([], { 'GET /books/10': problem(404, 'Not found', 'Book not found.') })

    expect(await screen.findByText('Book not found.')).toBeInTheDocument()
  })
})
