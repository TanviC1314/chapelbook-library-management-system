import { z } from 'zod'
import type { CreateUserInput } from '../../api/admin'
import type { Role, User } from '../../api/types'

// Mirrors the backend's validation; the backend remains the authority.

const password = z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password must be at most 72 characters')

export const createUserSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
    email: z
      .string()
      .trim()
      .min(1, 'Email is required')
      .max(254, 'Email must be at most 254 characters')
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Enter a valid email address'),
    role: z.enum(['STUDENT', 'LIBRARIAN', 'ADMIN']),
    studentNumber: z.string().trim().max(30, 'Student number must be at most 30 characters'),
    password,
  })
  .refine((v) => v.role === 'STUDENT' || v.studentNumber === '', {
    path: ['studentNumber'],
    message: 'Only students have a student number',
  })

export type CreateUserForm = z.infer<typeof createUserSchema>

export function toCreateUserInput(form: CreateUserForm): CreateUserInput {
  return {
    fullName: form.fullName,
    email: form.email,
    password: form.password,
    role: form.role,
    studentNumber: form.studentNumber || null,
  }
}

export const editUserSchema = z.object({
  fullName: z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
  role: z.enum(['STUDENT', 'LIBRARIAN', 'ADMIN']),
  active: z.boolean(),
})

export type EditUserForm = z.infer<typeof editUserSchema>

/** PATCH only what actually changed, so an edit never overwrites something it did not touch. */
export function changesFrom(user: User, form: EditUserForm): { fullName?: string; role?: Role; active?: boolean } {
  const changes: { fullName?: string; role?: Role; active?: boolean } = {}
  if (form.fullName !== user.fullName) changes.fullName = form.fullName
  if (form.role !== user.role) changes.role = form.role
  if (form.active !== user.active) changes.active = form.active
  return changes
}

export const resetPasswordSchema = z
  .object({ newPassword: password, confirmPassword: z.string() })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'The passwords do not match' })

export type ResetPasswordForm = z.infer<typeof resetPasswordSchema>
