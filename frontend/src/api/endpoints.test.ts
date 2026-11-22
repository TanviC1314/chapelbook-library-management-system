import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { statisticsApi, usersApi } from './admin'
import { catalogueApi } from './catalogue'
import { finesApi, loansApi, reservationsApi } from './circulation'
import { seatsApi } from './seats'

/** Checks each wrapper sends the exact method, path, query and body the backend documents (README endpoint table). */
describe('API endpoints', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    tokenStore.clear()
    fetchMock.mockResolvedValue(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  async function lastCall(run: () => Promise<unknown>) {
    await run()
    const [url, init] = fetchMock.mock.calls.at(-1)!
    return { url: url as string, method: init.method as string, body: init.body ? JSON.parse(init.body) : undefined }
  }

  it('searches the catalogue with only the filters that were set', async () => {
    const call = await lastCall(() =>
      catalogueApi.searchBooks({ q: 'clean code', categoryId: 3, availableOnly: true, sort: 'AUTHOR', page: 2 }),
    )

    expect(call.method).toBe('GET')
    expect(call.url).toBe('/api/v1/books?q=clean+code&categoryId=3&availableOnly=true&sort=AUTHOR&page=2')
  })

  it('leaves empty search fields out of the address', async () => {
    const call = await lastCall(() => catalogueApi.searchBooks({ q: '', availableOnly: false }))

    expect(call.url).toBe('/api/v1/books?availableOnly=false')
  })

  it.each([
    ['reserve', () => reservationsApi.reserve(5), 'POST', '/api/v1/reservations', { bookId: 5 }],
    ['cancel a reservation', () => reservationsApi.cancel(7), 'DELETE', '/api/v1/reservations/7', undefined],
    ['issue a loan', () => loansApi.issue({ userId: 2, copyBarcode: 'CB-1' }), 'POST', '/api/v1/loans', { userId: 2, copyBarcode: 'CB-1' }],
    ['return a copy', () => loansApi.returnCopy('CB-1'), 'POST', '/api/v1/loans/return', { copyBarcode: 'CB-1' }],
    ['renew a loan', () => loansApi.renew(9), 'POST', '/api/v1/loans/9/renew', undefined],
    ['pay a fine', () => finesApi.pay(4), 'POST', '/api/v1/fines/4/pay', undefined],
    ['waive a fine', () => finesApi.waive(4), 'POST', '/api/v1/fines/4/waive', undefined],
    ['my fine balance', () => finesApi.balance(), 'GET', '/api/v1/fines/mine/balance', undefined],
    ['book a seat', () => seatsApi.book({ seatId: 1, startsAt: 'a', endsAt: 'b' }), 'POST', '/api/v1/seat-bookings', { seatId: 1, startsAt: 'a', endsAt: 'b' }],
    ['cancel a seat booking', () => seatsApi.cancel(3), 'DELETE', '/api/v1/seat-bookings/3', undefined],
    ['patch a copy', () => catalogueApi.updateCopy(8, { status: 'LOST' }), 'PATCH', '/api/v1/copies/8', { status: 'LOST' }],
    ['deactivate a user', () => usersApi.update(6, { active: false }), 'PATCH', '/api/v1/users/6', { active: false }],
    ['reset a password', () => usersApi.resetPassword(6, 'new-password-1'), 'POST', '/api/v1/users/6/password', { newPassword: 'new-password-1' }],
  ])('%s', async (_name, run, method, url, body) => {
    const call = await lastCall(run)

    expect(call.method).toBe(method)
    expect(call.url).toBe(url)
    expect(call.body).toEqual(body)
  })

  it('asks which seats are free for a window', async () => {
    const call = await lastCall(() =>
      seatsApi.list({ zone: 'Quiet', from: '2030-03-01T09:00:00.000Z', to: '2030-03-01T11:00:00.000Z' }),
    )

    expect(call.url).toBe('/api/v1/seats?zone=Quiet&from=2030-03-01T09%3A00%3A00.000Z&to=2030-03-01T11%3A00%3A00.000Z')
  })

  it('scopes staff loan lists', async () => {
    const call = await lastCall(() => loansApi.list({ scope: 'OVERDUE', page: 1 }))

    expect(call.url).toBe('/api/v1/loans?scope=OVERDUE&page=1')
  })

  it('requests statistics for a date range', async () => {
    const call = await lastCall(() => statisticsApi.circulation({ from: '2030-03-01', to: '2030-03-31' }))

    expect(call.url).toBe('/api/v1/statistics/circulation?from=2030-03-01&to=2030-03-31')
  })

  it('searches users', async () => {
    const call = await lastCall(() => usersApi.list({ q: 'ann', role: 'STUDENT', active: true }))

    expect(call.url).toBe('/api/v1/users?q=ann&role=STUDENT&active=true')
  })
})
