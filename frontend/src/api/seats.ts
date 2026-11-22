import { request } from './client'
import type { PageResponse, Seat, SeatBooking, SeatStatus } from './types'
import type { Paging } from './circulation'

export interface SeatInput {
  label: string
  zone: string
  hasPower: boolean
  status?: SeatStatus
}

export const seatsApi = {
  /** Pass both from and to (ISO instants) to learn which seats are free for that whole window. */
  list: (filter: { zone?: string; from?: string; to?: string } = {}) => request<Seat[]>('/seats', { query: { ...filter } }),
  create: (input: SeatInput) => request<Seat>('/seats', { method: 'POST', body: input }),
  update: (id: number, input: SeatInput) => request<Seat>(`/seats/${id}`, { method: 'PUT', body: input }),

  book: (input: { seatId: number; startsAt: string; endsAt: string }) =>
    request<SeatBooking>('/seat-bookings', { method: 'POST', body: input }),
  mine: (filter: { upcomingOnly?: boolean } & Paging = {}) =>
    request<PageResponse<SeatBooking>>('/seat-bookings/mine', { query: { ...filter } }),
  listBookings: (filter: { from?: string; to?: string } & Paging = {}) =>
    request<PageResponse<SeatBooking>>('/seat-bookings', { query: { ...filter } }),
  cancel: (id: number) => request<SeatBooking>(`/seat-bookings/${id}`, { method: 'DELETE' }),
}
