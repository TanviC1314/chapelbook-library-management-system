import { describe, expect, it } from 'vitest'
import { loginSchema, registerSchema } from './schemas'

const valid = {
  fullName: 'Ann Student',
  email: 'ann@example.com',
  studentNumber: '',
  password: 'correct-horse',
  confirmPassword: 'correct-horse',
}

function messages(input: unknown): Record<string, string> {
  const result = registerSchema.safeParse(input)
  if (result.success) return {}
  return Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('loginSchema', () => {
  it('requires both fields', () => {
    const result = loginSchema.safeParse({ email: ' ', password: '' })

    expect(result.success).toBe(false)
  })

  it('refuses a password longer than the 72 characters BCrypt can use', () => {
    expect(loginSchema.safeParse({ email: 'a@b.co', password: 'x'.repeat(73) }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'a@b.co', password: 'x'.repeat(72) }).success).toBe(true)
  })
})

describe('registerSchema', () => {
  it('accepts a complete registration with no student number', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it('accepts a student number', () => {
    expect(registerSchema.safeParse({ ...valid, studentNumber: 'S-1001' }).success).toBe(true)
  })

  it.each([
    [{ fullName: '  ' }, 'fullName', 'Name is required'],
    [{ email: 'not-an-email' }, 'email', 'Enter a valid email address'],
    [{ password: 'short', confirmPassword: 'short' }, 'password', 'Password must be at least 8 characters'],
    [{ password: 'x'.repeat(73), confirmPassword: 'x'.repeat(73) }, 'password', 'Password must be at most 72 characters'],
    [{ studentNumber: 'S'.repeat(31) }, 'studentNumber', 'Student number must be at most 30 characters'],
  ])('rejects %j', (override, field, message) => {
    expect(messages({ ...valid, ...override })[field]).toBe(message)
  })

  it('reports mismatched passwords on the confirmation field', () => {
    expect(messages({ ...valid, confirmPassword: 'something-else' }).confirmPassword).toBe('The passwords do not match')
  })
})
