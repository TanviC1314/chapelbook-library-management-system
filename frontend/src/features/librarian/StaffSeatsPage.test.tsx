import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { makeSeat, makeSeatBooking } from '../../test/fixtures'
import { json, makeToken, makeUser, mockApi, page, problem, renderApp } from '../../test/utils'
import StaffSeatsPage from './StaffSeatsPage'

const seats = [
  makeSeat({ id: 1, label: 'Q-01', zone: 'Quiet', hasPower: true }),
  makeSeat({ id: 2, label: 'Q-02', zone: 'Quiet', hasPower: false, status: 'OUT_OF_SERVICE' }),
]

function renderPage(routes: Parameters<typeof mockApi>[0] = {}) {
  tokenStore.set(makeToken())
  const api = mockApi({
    'GET /users/me': json(makeUser('LIBRARIAN')),
    'GET /seats': json(seats),
    'GET /seat-bookings': json(page([])),
    ...routes,
  })
  renderApp(
    <Routes>
      <Route path="/librarian/seats" element={<StaffSeatsPage />} />
    </Routes>,
    { route: '/librarian/seats' },
  )
  return api
}

describe('StaffSeatsPage', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it('lists every seat including ones out of service', async () => {
    renderPage()

    expect(await screen.findByText('Q-01')).toBeInTheDocument()
    expect(screen.getByText('Q-02')).toBeInTheDocument()
    expect(screen.getByText('Out of service')).toBeInTheDocument()
    expect(screen.getByLabelText('Has a power socket')).toBeInTheDocument()
  })

  it('adds a seat', async () => {
    const api = renderPage({ 'POST /seats': json(makeSeat({ id: 3, label: 'G-01', zone: 'Group', hasPower: false }), 201) })

    await userEvent.click(await screen.findByRole('button', { name: 'Add a seat' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Label'), 'G-01')
    await userEvent.type(within(dialog).getByLabelText('Zone'), 'Group')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add seat' }))

    expect(await screen.findByText('Seat G-01 added.')).toBeInTheDocument()
    expect(api.callsTo('POST', '/seats')[0].body).toEqual({ label: 'G-01', zone: 'Group', hasPower: false, status: 'ACTIVE' })
  })

  it('needs a label and a zone', async () => {
    const api = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a seat' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Add seat' }))

    expect(await screen.findByText('Label is required')).toBeInTheDocument()
    expect(screen.getByText('Zone is required')).toBeInTheDocument()
    expect(api.callsTo('POST', '/seats')).toHaveLength(0)
  })

  it('shows the reason when a label is already used', async () => {
    renderPage({ 'POST /seats': problem(409, 'Conflict', 'A seat labelled "Q-01" already exists.') })

    await userEvent.click(await screen.findByRole('button', { name: 'Add a seat' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Label'), 'Q-01')
    await userEvent.type(within(dialog).getByLabelText('Zone'), 'Quiet')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add seat' }))

    expect(await within(dialog).findByText('A seat labelled "Q-01" already exists.')).toBeInTheDocument()
  })

  it('edits a seat with its current values and can take it out of service', async () => {
    const api = renderPage({ 'PUT /seats/1': json(makeSeat({ status: 'OUT_OF_SERVICE' })) })

    await userEvent.click(await screen.findByRole('button', { name: 'Edit seat Q-01' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Label')).toHaveValue('Q-01')
    expect(within(dialog).getByLabelText('Has a power socket')).toBeChecked()
    await userEvent.selectOptions(within(dialog).getByLabelText('Status'), 'Out of service')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Seat Q-01 saved.')).toBeInTheDocument()
    expect(api.callsTo('PUT', '/seats/1')[0].body).toEqual({ label: 'Q-01', zone: 'Quiet', hasPower: true, status: 'OUT_OF_SERVICE' })
  })

  it('explains why a seat with upcoming bookings cannot be taken out of service', async () => {
    renderPage({ 'PUT /seats/1': problem(409, 'Conflict', 'This seat has upcoming bookings. Cancel them before taking it out of service.') })

    await userEvent.click(await screen.findByRole('button', { name: 'Edit seat Q-01' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.selectOptions(within(dialog).getByLabelText('Status'), 'Out of service')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await within(dialog).findByText(/upcoming bookings\. Cancel them before/)).toBeInTheDocument()
  })

  it('shows bookings in the chosen dates with who made them, as whole days', async () => {
    const api = renderPage({ 'GET /seat-bookings': json(page([makeSeatBooking({ userName: 'Ann Student', seatLabel: 'Q-01' })])) })

    expect(await screen.findByText(/Ann Student · /)).toBeInTheDocument()
    const query = new URL(String(api.fetchMock.mock.calls.find((c) => String(c[0]).includes('/seat-bookings'))![0]), 'http://x').searchParams
    const spanMs = Date.parse(query.get('to')!) - Date.parse(query.get('from')!)
    expect(Math.round(spanMs / 86_400_000)).toBe(7) // today plus six more days, inclusive
  })

  it('refuses a backwards date range without asking the server', async () => {
    const api = renderPage()
    await screen.findByText('Q-01')
    const before = api.callsTo('GET', '/seat-bookings').length

    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2000-01-01' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a start date, and an end date that is not before it.')
    expect(api.callsTo('GET', '/seat-bookings')).toHaveLength(before)
  })

  it('cancels a booking after confirmation', async () => {
    const api = renderPage({
      'GET /seat-bookings': json(page([makeSeatBooking({ id: 4, userName: 'Ann Student' })])),
      'DELETE /seat-bookings/4': json(makeSeatBooking({ id: 4, status: 'CANCELLED' })),
    })

    await userEvent.click(await screen.findByRole('button', { name: /Cancel Ann Student's booking of seat Q-01/ }))
    const dialog = await screen.findByRole('dialog')
    expect(api.callsTo('DELETE', '/seat-bookings/4')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel booking' }))

    expect(await screen.findByText('Booking cancelled.')).toBeInTheDocument()
    expect(api.callsTo('DELETE', '/seat-bookings/4')).toHaveLength(1)
  })

  it('says when nothing is booked, and has an error state with retry', async () => {
    let calls = 0
    renderPage({ 'GET /seats': () => (++calls === 1 ? problem(500, 'Server error', 'Something went wrong on our side.') : json(seats)) })

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Q-01')).toBeInTheDocument()
    expect(screen.getByText('No seats are booked in this period.')).toBeInTheDocument()
  })
})
