import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import type { AuthResponse, Role, User } from '../api/types'
import { AuthProvider } from '../auth/AuthContext'
import { ToastProvider } from '../components/ui/Toast'

export function makeUser(role: Role = 'STUDENT', overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: `${role.toLowerCase()}@example.com`,
    fullName: `Test ${role.toLowerCase()}`,
    role,
    studentNumber: role === 'STUDENT' ? 'S-1' : null,
    active: true,
    ...overrides,
  }
}

/** A JWT-shaped token that is valid for an hour, enough for the client to treat the session as live. */
export function makeToken(secondsFromNow = 3600): string {
  const payload = btoa(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + secondsFromNow }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `header.${payload}.signature`
}

export function makeAuthResponse(user: User): AuthResponse {
  return { accessToken: makeToken(), tokenType: 'Bearer', expiresInSeconds: 3600, user }
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

export function problem(status: number, title: string, detail: string, errors?: { field: string; message: string }[]): Response {
  return json({ status, title, detail, ...(errors ? { errors } : {}) }, status)
}

export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/** A page of results in the backend's paging shape. */
export function page<T>(content: T[], overrides: Partial<{ page: number; size: number; totalElements: number; totalPages: number }> = {}) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...overrides }
}

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response> | undefined

/**
 * Routes the app's fetch calls to handlers by method and path, and fails loudly if the app asks for something the
 * test did not expect, instead of hanging or silently returning nothing.
 */
export function mockApi(routes: Record<string, Handler | Response>) {
  const calls: { method: string; path: string; body: unknown }[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost')
    const method = (init.method ?? 'GET').toUpperCase()
    const path = url.pathname.replace('/api/v1', '')
    calls.push({ method, path, body: init.body ? JSON.parse(String(init.body)) : undefined })
    const route = routes[`${method} ${path}`]
    if (route === undefined) throw new Error(`Unexpected request: ${method} ${path}${url.search}`)
    const result = route instanceof Response ? route.clone() : await route(url, init)
    if (!result) throw new Error(`Handler returned nothing for ${method} ${path}`)
    return result
  })
  vi.stubGlobal('fetch', fetchMock)
  return { fetchMock, calls, callsTo: (method: string, path: string) => calls.filter((c) => c.method === method && c.path === path) }
}

export function renderApp(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <ToastProvider>{ui}</ToastProvider>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
