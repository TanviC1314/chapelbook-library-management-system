import { request } from './client'
import type { Fine, FineBalance, FineStatus, Loan, PageResponse, Reservation, ReservationStatus } from './types'

export interface Paging {
  page?: number
  size?: number
}

export type LoanScope = 'ALL' | 'ACTIVE' | 'OVERDUE'

export const reservationsApi = {
  reserve: (bookId: number) => request<Reservation>('/reservations', { method: 'POST', body: { bookId } }),
  mine: (paging: Paging = {}) => request<PageResponse<Reservation>>('/reservations/mine', { query: { ...paging } }),
  list: (filter: { status?: ReservationStatus } & Paging = {}) =>
    request<PageResponse<Reservation>>('/reservations', { query: { ...filter } }),
  cancel: (id: number) => request<Reservation>(`/reservations/${id}`, { method: 'DELETE' }),
}

export const loansApi = {
  issue: (input: { userId: number; copyBarcode: string }) => request<Loan>('/loans', { method: 'POST', body: input }),
  returnCopy: (copyBarcode: string) => request<Loan>('/loans/return', { method: 'POST', body: { copyBarcode } }),
  renew: (id: number) => request<Loan>(`/loans/${id}/renew`, { method: 'POST' }),
  mine: (filter: { activeOnly?: boolean } & Paging = {}) => request<PageResponse<Loan>>('/loans/mine', { query: { ...filter } }),
  list: (filter: { userId?: number; scope?: LoanScope } & Paging = {}) =>
    request<PageResponse<Loan>>('/loans', { query: { ...filter } }),
}

export const finesApi = {
  mine: (filter: { status?: FineStatus } & Paging = {}) => request<PageResponse<Fine>>('/fines/mine', { query: { ...filter } }),
  balance: () => request<FineBalance>('/fines/mine/balance'),
  list: (filter: { userId?: number; status?: FineStatus } & Paging = {}) =>
    request<PageResponse<Fine>>('/fines', { query: { ...filter } }),
  pay: (id: number) => request<Fine>(`/fines/${id}/pay`, { method: 'POST' }),
  waive: (id: number) => request<Fine>(`/fines/${id}/waive`, { method: 'POST' }),
}
