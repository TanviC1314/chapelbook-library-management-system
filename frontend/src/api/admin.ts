import { request } from './client'
import type { Paging } from './circulation'
import type { CirculationStats, Overview, PageResponse, Role, SeatStats, User } from './types'

export interface UserSearch {
  q?: string
  role?: Role
  active?: boolean
}

export interface CreateUserInput {
  email: string
  password: string
  fullName: string
  role: Role
  studentNumber?: string | null
}

export const usersApi = {
  list: (search: UserSearch & Paging = {}) => request<PageResponse<User>>('/users', { query: { ...search } }),
  get: (id: number) => request<User>(`/users/${id}`),
  create: (input: CreateUserInput) => request<User>('/users', { method: 'POST', body: input }),
  update: (id: number, input: { fullName?: string; role?: Role; active?: boolean }) =>
    request<User>(`/users/${id}`, { method: 'PATCH', body: input }),
  resetPassword: (id: number, newPassword: string) =>
    request<void>(`/users/${id}/password`, { method: 'POST', body: { newPassword } }),
}

export const statisticsApi = {
  overview: () => request<Overview>('/statistics/overview'),
  circulation: (range: { from?: string; to?: string } = {}) =>
    request<CirculationStats>('/statistics/circulation', { query: { ...range } }),
  seats: (range: { from?: string; to?: string } = {}) => request<SeatStats>('/statistics/seats', { query: { ...range } }),
}
