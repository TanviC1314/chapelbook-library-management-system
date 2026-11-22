import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { computing, history, makeBook } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import CataloguePage from './CataloguePage'

function Where() {
  const location = useLocation()
  return <p data-testid="where">{location.pathname + location.search}</p>
}

function renderCatalogue(route = '/catalogue') {
  return renderApp(
    <>
      <Routes>
        <Route path="/catalogue" element={<CataloguePage />} />
        <Route path="/catalogue/:id" element={<p>detail page</p>} />
      </Routes>
      <Where />
    </>,
    { route },
  )
}

function signedIn() {
  tokenStore.set(makeToken())
}

describe('CataloguePage', () => {
  beforeEach(signedIn)
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  function api(searchHandler: Parameters<typeof mockApi>[0][string]) {
    return mockApi({
      'GET /users/me': json(makeUser('STUDENT')),
      'GET /categories': json([computing, history]),
      'GET /books': searchHandler,
    })
  }

  it('lists books with their category, author, year and availability', async () => {
    api(json(page([makeBook(), makeBook({ id: 11, title: 'Sapiens', author: 'Yuval Harari', category: history, publicationYear: 2011, availableCopies: 0, totalCopies: 2, pendingReservations: 3 })])))
    renderCatalogue()

    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
    expect(screen.getByText('2 of 3 available')).toBeInTheDocument()
    expect(screen.getByText('Yuval Harari · 2011')).toBeInTheDocument()
    expect(screen.getByText('All copies out, 3 waiting')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Clean Code/ })).toHaveAttribute('href', '/catalogue/10')
  })

  it('searches only after the person pauses typing, and puts the search in the address', async () => {
    const mock = api(json(page([makeBook()])))
    renderCatalogue()
    await screen.findByText('Clean Code')

    await userEvent.type(screen.getByLabelText('Search'), 'martin')

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/catalogue?q=martin'))
    const searches = mock.callsTo('GET', '/books')
    expect(searches.length).toBeLessThanOrEqual(2) // the first load, then one search, not one per keystroke
    await waitFor(() => expect(mock.fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('q=martin'), expect.anything()))
  })

  it('filters by category, available-only and sort, and starts again from page one', async () => {
    const mock = api(json(page([makeBook()])))
    renderCatalogue('/catalogue?page=2')
    await screen.findByText('Clean Code')

    await userEvent.selectOptions(screen.getByLabelText('Category'), 'History')
    await userEvent.click(screen.getByLabelText('Only show books available now'))
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'Author')

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('categoryId=2'))
    expect(screen.getByTestId('where')).toHaveTextContent('availableOnly=true')
    expect(screen.getByTestId('where')).toHaveTextContent('sort=AUTHOR')
    expect(screen.getByTestId('where')).not.toHaveTextContent('page=')
    const last = new URL(String(mock.fetchMock.mock.calls.at(-1)![0]), 'http://x')
    expect(last.searchParams.get('categoryId')).toBe('2')
    expect(last.searchParams.get('availableOnly')).toBe('true')
    expect(last.searchParams.get('sort')).toBe('AUTHOR')
  })

  it('reads its filters from the address, so a shared link shows the same results', async () => {
    const mock = api(json(page([makeBook()])))
    renderCatalogue('/catalogue?q=code&categoryId=1&availableOnly=true&sort=YEAR&page=1')

    await screen.findByText('Clean Code')

    const first = new URL(String(mock.callsTo('GET', '/books').length ? mock.fetchMock.mock.calls.find((c) => String(c[0]).includes('/books'))![0] : ''), 'http://x')
    expect(Object.fromEntries(first.searchParams)).toMatchObject({ q: 'code', categoryId: '1', availableOnly: 'true', sort: 'YEAR', page: '1' })
    expect(screen.getByLabelText('Search')).toHaveValue('code')
    expect(screen.getByLabelText('Only show books available now')).toBeChecked()
  })

  it('pages through the results', async () => {
    const mock = api((url) => json(page([makeBook({ title: `Page ${url.searchParams.get('page') ?? '0'} book` })], { page: Number(url.searchParams.get('page') ?? 0), totalElements: 30, totalPages: 3 })))
    renderCatalogue()
    await screen.findByText('Page 0 book')
    expect(screen.getByText(/Page 1 of 3 · 30 books/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByText('Page 1 book')).toBeInTheDocument()
    expect(new URL(String(mock.fetchMock.mock.calls.at(-1)![0]), 'http://x').searchParams.get('page')).toBe('1')
  })

  it('says so when nothing matches', async () => {
    api(json(page([])))
    renderCatalogue()

    expect(await screen.findByText('No books match your search')).toBeInTheDocument()
  })

  it('shows an error with a retry that works', async () => {
    let calls = 0
    api(() => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(page([makeBook()]))))
    renderCatalogue()

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Clean Code')).toBeInTheDocument()
  })

  it('ignores a nonsense sort or page in the address instead of breaking', async () => {
    const mock = api(json(page([makeBook()])))
    renderCatalogue('/catalogue?sort=PASSWORD&page=-4')

    await screen.findByText('Clean Code')

    const url = new URL(String(mock.fetchMock.mock.calls.find((c) => String(c[0]).includes('/books'))![0]), 'http://x')
    expect(url.searchParams.get('sort')).toBe('TITLE')
    expect(url.searchParams.get('page')).toBe('0') // a negative page is clamped to the first page
  })
})
