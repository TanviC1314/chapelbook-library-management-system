import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { computing, history, makeBook } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, noContent, page, problem, renderApp } from '../../test/utils'
import ManageBooksPage from './ManageBooksPage'

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('LIBRARIAN')),
    'GET /categories': json([computing, history]),
    'GET /books': json(page([makeBook()])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route path="/librarian/books" element={<ManageBooksPage />} />
      <Route path="/librarian/books/:id" element={<p>copies page</p>} />
    </Routes>,
    { route: '/librarian/books' },
  )
  return api
}

async function fillBookForm(values: { title?: string; author?: string; category?: string; year?: string; isbn?: string }) {
  const dialog = await screen.findByRole('dialog')
  if (values.title !== undefined) await userEvent.type(within(dialog).getByLabelText('Title'), values.title)
  if (values.author !== undefined) await userEvent.type(within(dialog).getByLabelText('Author'), values.author)
  if (values.category) await userEvent.selectOptions(within(dialog).getByLabelText('Category'), values.category)
  if (values.isbn) await userEvent.type(within(dialog).getByLabelText('ISBN (optional)'), values.isbn)
  if (values.year) await userEvent.type(within(dialog).getByLabelText('Year (optional)'), values.year)
  return dialog
}

describe('ManageBooksPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('lists titles with category, ISBN, availability and the queue', async () => {
    renderPage({ 'GET /books': json(page([makeBook({ pendingReservations: 2 })])) })

    expect(await screen.findByRole('link', { name: 'Clean Code' })).toHaveAttribute('href', '/librarian/books/10')
    expect(screen.getByText(/Robert C\. Martin · 9780132350884/)).toBeInTheDocument()
    expect(screen.getByText('2 of 3 available')).toBeInTheDocument()
    expect(screen.getByText('2 waiting')).toBeInTheDocument()
  })

  it('has a helpful empty state with a way to add the first book', async () => {
    renderPage({ 'GET /books': json(page([])) })

    expect(await screen.findByText('No books found')).toBeInTheDocument()
    expect(screen.getByText('Add the first book to the library.')).toBeInTheDocument()
  })

  it('searches after typing pauses', async () => {
    const api = renderPage()
    await screen.findByText('Clean Code')

    await userEvent.type(screen.getByLabelText('Search'), 'martin')

    await waitFor(() => expect(new URL(String(api.fetchMock.mock.calls.at(-1)![0]), 'http://x').searchParams.get('q')).toBe('martin'))
  })

  it('adds a book, sending numbers and nulls in the shape the API expects', async () => {
    const api = renderPage({ 'POST /books': json(makeBook({ id: 11, title: 'New Book' }), 201) })
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Add a book' }))
    const dialog = await fillBookForm({ title: 'New Book', author: 'A. Writer', category: 'History', year: '2021' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add book' }))

    expect(await screen.findByText(/Added “New Book”/)).toBeInTheDocument()
    expect(api.callsTo('POST', '/books')[0].body).toEqual({
      title: 'New Book',
      author: 'A. Writer',
      isbn: null,
      publisher: null,
      publicationYear: 2021,
      description: null,
      categoryId: 2,
    })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('checks the form before sending anything', async () => {
    const api = renderPage()
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Add a book' }))
    const dialog = await fillBookForm({})
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add book' }))

    expect(await within(dialog).findByText('Title is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Author is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Choose a category', { selector: 'p' })).toBeInTheDocument() // not the dropdown placeholder
    expect(api.callsTo('POST', '/books')).toHaveLength(0)
  })

  it('puts the server field errors next to the fields and keeps the form open', async () => {
    renderPage({
      'POST /books': problem(422, 'Rule violated', 'An ISBN must have 10 or 13 digits.'),
    })
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Add a book' }))
    const dialog = await fillBookForm({ title: 'T', author: 'A', category: 'Computing', isbn: '12345-6789-01' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add book' }))

    expect(await within(dialog).findByText('An ISBN must have 10 or 13 digits.')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('edits a book with its current values filled in', async () => {
    const api = renderPage({ 'PUT /books/10': json(makeBook({ title: 'Clean Code, 2nd ed.' })) })
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Edit Clean Code' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Title')).toHaveValue('Clean Code')
    expect(within(dialog).getByLabelText('Category')).toHaveValue('1')
    expect(within(dialog).getByLabelText('Year (optional)')).toHaveValue('2008')
    await userEvent.clear(within(dialog).getByLabelText('Title'))
    await userEvent.type(within(dialog).getByLabelText('Title'), 'Clean Code, 2nd ed.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText(/Saved “Clean Code, 2nd ed\.”/)).toBeInTheDocument()
    expect(api.callsTo('PUT', '/books/10')[0].body).toMatchObject({ title: 'Clean Code, 2nd ed.', categoryId: 1, publicationYear: 2008 })
  })

  it('deletes only after confirmation', async () => {
    const api = renderPage({ 'DELETE /books/10': noContent() })
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    const dialog = await screen.findByRole('dialog')
    expect(api.callsTo('DELETE', '/books/10')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete book' }))

    expect(await screen.findByText('Book deleted.')).toBeInTheDocument()
    expect(api.callsTo('DELETE', '/books/10')).toHaveLength(1)
  })

  it('explains why a book with copies cannot be deleted', async () => {
    renderPage({
      'DELETE /books/10': problem(409, 'Conflict', 'This book has copies or reservations, so its history must be kept. Withdraw its copies instead.'),
    })
    await screen.findByText('Clean Code')

    await userEvent.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete book' }))

    expect(await screen.findByText(/Withdraw its copies instead/, { selector: 'p' })).toBeInTheDocument()
  })

  it('shows an error with a retry', async () => {
    let calls = 0
    renderPage({ 'GET /books': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([makeBook()]))) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
  })
})

describe('Categories modal', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  async function openModal(routes: Parameters<typeof mockApi>[0] = {}) {
    const api = renderPage(routes)
    await screen.findByText('Clean Code')
    await userEvent.click(screen.getByRole('button', { name: 'Categories' }))
    return { api, dialog: await screen.findByRole('dialog') }
  }

  it('lists the categories and adds a new one', async () => {
    const { api, dialog } = await openModal({ 'POST /categories': json({ id: 3, name: 'Poetry', description: null }, 201) })
    expect(await within(dialog).findByText('History')).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText('Name'), 'Poetry')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add category' }))

    expect(await screen.findByText('Added the category “Poetry”.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/categories')[0].body).toEqual({ name: 'Poetry', description: null })
  })

  it('renames a category', async () => {
    const { api, dialog } = await openModal({ 'PUT /categories/2': json({ id: 2, name: 'World history', description: null }) })

    await userEvent.click(await within(dialog).findByRole('button', { name: 'Rename History' }))
    expect(within(dialog).getByLabelText('Name')).toHaveValue('History')
    await userEvent.clear(within(dialog).getByLabelText('Name'))
    await userEvent.type(within(dialog).getByLabelText('Name'), 'World history')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Renamed to “World history”.')).toBeInTheDocument()
    expect(api.callsTo('PUT', '/categories/2')[0].body).toMatchObject({ name: 'World history' })
  })

  it('shows the reason when a name is already taken', async () => {
    const { dialog } = await openModal({ 'POST /categories': problem(409, 'Conflict', 'A category named "history" already exists.') })

    await userEvent.type(within(dialog).getByLabelText('Name'), 'history')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add category' }))

    expect(await within(dialog).findByText('A category named "history" already exists.')).toBeInTheDocument()
  })

  it('refuses to delete a category that still has books, and says why', async () => {
    const { dialog } = await openModal({ 'DELETE /categories/1': problem(409, 'Conflict', 'This category still has books. Move or remove them first.') })

    await userEvent.click(await within(dialog).findByRole('button', { name: 'Delete Computing' }))
    const confirm = (await screen.findAllByRole('dialog')).at(-1)!
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete category' }))

    expect(await screen.findByText('This category still has books. Move or remove them first.', { selector: 'p' })).toBeInTheDocument()
  })
})
