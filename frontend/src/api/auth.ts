import { request } from './client'
import type { AuthResponse, User } from './types'

export interface RegisterInput {
  email: string
  password: string
  fullName: string
  studentNumber?: string
}

export const authApi = {
  login: (input: { email: string; password: string }) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: input }),
  register: (input: RegisterInput) => request<AuthResponse>('/auth/register', { method: 'POST', body: input }),
  me: () => request<User>('/users/me'),
}
