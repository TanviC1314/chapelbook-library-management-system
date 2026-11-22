import { z } from 'zod'
import type { BookInput } from '../../api/catalogue'

// Mirrors the backend's validation so staff see a problem before a round trip; the backend remains the authority.

export const bookSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(300, 'Title must be at most 300 characters'),
  author: z.string().trim().min(1, 'Author is required').max(200, 'Author must be at most 200 characters'),
  isbn: z
    .string()
    .trim()
    .refine((v) => v === '' || /^[0-9Xx -]{10,20}$/.test(v), 'An ISBN uses digits, X, spaces and hyphens (10 to 20 characters)'),
  publisher: z.string().trim().max(200, 'Publisher must be at most 200 characters'),
  publicationYear: z
    .string()
    .trim()
    .refine((v) => v === '' || (/^\d{4}$/.test(v) && Number(v) >= 1000 && Number(v) <= 2200), 'Enter a four digit year between 1000 and 2200'),
  description: z.string().trim().max(2000, 'Description must be at most 2000 characters'),
  categoryId: z.string().min(1, 'Choose a category'),
})

export type BookForm = z.infer<typeof bookSchema>

/** The form works in strings; the API wants numbers, and null for anything left blank. */
export function toBookInput(form: BookForm): BookInput {
  return {
    title: form.title,
    author: form.author,
    isbn: form.isbn || null,
    publisher: form.publisher || null,
    publicationYear: form.publicationYear ? Number(form.publicationYear) : null,
    description: form.description || null,
    categoryId: Number(form.categoryId),
  }
}

export const categorySchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
  description: z.string().trim().max(500, 'Description must be at most 500 characters'),
})

export type CategoryForm = z.infer<typeof categorySchema>
