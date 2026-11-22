import { describe, expect, it } from 'vitest'
import { navFor } from './navigation'

const paths = (role: Parameters<typeof navFor>[0]) => navFor(role).map((item) => item.to)

describe('navigation per role', () => {
  it('shows students only student pages and the catalogue', () => {
    expect(paths('STUDENT')).toEqual([
      '/student',
      '/catalogue',
      '/student/borrowings',
      '/student/reservations',
      '/student/fines',
      '/student/seats',
    ])
  })

  it('never offers a student the staff or admin areas', () => {
    expect(paths('STUDENT').some((p) => p.startsWith('/librarian') || p.startsWith('/admin'))).toBe(false)
  })

  it('gives librarians the desk tools but not the admin pages', () => {
    expect(paths('LIBRARIAN')).toContain('/librarian/desk')
    expect(paths('LIBRARIAN')).toContain('/librarian/books')
    expect(paths('LIBRARIAN').some((p) => p.startsWith('/admin'))).toBe(false)
  })

  it('gives admins the admin pages plus everything a librarian has', () => {
    const admin = paths('ADMIN')

    expect(admin).toEqual(expect.arrayContaining(['/admin', '/admin/users', '/admin/reports']))
    for (const librarianPath of paths('LIBRARIAN').filter((p) => p !== '/librarian')) {
      expect(admin).toContain(librarianPath)
    }
  })

  it('has no duplicate links', () => {
    for (const role of ['STUDENT', 'LIBRARIAN', 'ADMIN'] as const) {
      expect(new Set(paths(role)).size).toBe(paths(role).length)
    }
  })
})
