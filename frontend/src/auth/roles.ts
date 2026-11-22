import type { Role } from '../api/types'

/** The landing page of each portal. */
export function homeFor(role: Role): string {
  switch (role) {
    case 'STUDENT':
      return '/student'
    case 'LIBRARIAN':
      return '/librarian'
    case 'ADMIN':
      return '/admin'
  }
}

export function isStaff(role: Role): boolean {
  return role === 'LIBRARIAN' || role === 'ADMIN'
}

export const roleLabel: Record<Role, string> = {
  STUDENT: 'Student',
  LIBRARIAN: 'Librarian',
  ADMIN: 'Administrator',
}
