import { z } from 'zod'

// These mirror the rules the backend enforces, so people see a problem before a round trip.
// The backend remains the authority; its messages are shown if it disagrees.

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required').max(72, 'Password must be at most 72 characters'),
})

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
    email: z
      .string()
      .trim()
      .min(1, 'Email is required')
      .max(254, 'Email must be at most 254 characters')
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Enter a valid email address'),
    studentNumber: z.string().trim().max(30, 'Student number must be at most 30 characters'),
    password: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password must be at most 72 characters'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match',
  })

export type LoginForm = z.infer<typeof loginSchema>
export type RegisterForm = z.infer<typeof registerSchema>
