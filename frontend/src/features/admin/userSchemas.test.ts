import { describe, expect, it } from 'vitest'
import { makeUser } from '../../test/utils'
import { changesFrom, createUserSchema, resetPasswordSchema, toCreateUserInput, type CreateUserForm } from './userSchemas'

const valid: CreateUserForm = {
  fullName: 'Lena Librarian',
  email: 'lena@example.com',
  role: 'LIBRARIAN',
  studentNumber: '',
  password: 'correct-horse',
}

function problems(input: unknown): Record<string, string> {
  const result = createUserSchema.safeParse(input)
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('createUserSchema', () => {
  it('accepts a staff account and a student with a number', () => {
    expect(createUserSchema.safeParse(valid).success).toBe(true)
    expect(createUserSchema.safeParse({ ...valid, role: 'STUDENT', studentNumber: 'S-1' }).success).toBe(true)
  })

  it.each([
    [{ fullName: ' ' }, 'fullName', 'Name is required'],
    [{ email: 'nope' }, 'email', 'Enter a valid email address'],
    [{ password: 'short' }, 'password', 'Password must be at least 8 characters'],
    [{ password: 'x'.repeat(73) }, 'password', 'Password must be at most 72 characters'],
    [{ studentNumber: 'S-1' }, 'studentNumber', 'Only students have a student number'],
  ])('rejects %j', (override, field, message) => {
    expect(problems({ ...valid, ...override })[field]).toBe(message)
  })

  it('turns a blank student number into null for the API', () => {
    expect(toCreateUserInput(valid)).toEqual({
      fullName: 'Lena Librarian',
      email: 'lena@example.com',
      password: 'correct-horse',
      role: 'LIBRARIAN',
      studentNumber: null,
    })
  })
})

describe('changesFrom', () => {
  const user = makeUser('LIBRARIAN', { fullName: 'Lena', active: true })

  it('sends nothing when nothing changed', () => {
    expect(changesFrom(user, { fullName: 'Lena', role: 'LIBRARIAN', active: true })).toEqual({})
  })

  it('sends only the fields that changed', () => {
    expect(changesFrom(user, { fullName: 'Lena L.', role: 'LIBRARIAN', active: true })).toEqual({ fullName: 'Lena L.' })
    expect(changesFrom(user, { fullName: 'Lena', role: 'ADMIN', active: false })).toEqual({ role: 'ADMIN', active: false })
  })
})

describe('resetPasswordSchema', () => {
  it('requires a matching password of a valid length', () => {
    expect(resetPasswordSchema.safeParse({ newPassword: 'long-enough-1', confirmPassword: 'long-enough-1' }).success).toBe(true)
    expect(resetPasswordSchema.safeParse({ newPassword: 'long-enough-1', confirmPassword: 'different-one' }).success).toBe(false)
    expect(resetPasswordSchema.safeParse({ newPassword: 'short', confirmPassword: 'short' }).success).toBe(false)
  })
})
