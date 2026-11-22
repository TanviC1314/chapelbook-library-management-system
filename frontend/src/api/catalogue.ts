import { request } from './client'
import type { Book, Category, Copy, CopyStatus, PageResponse } from './types'

export type BookSort = 'TITLE' | 'AUTHOR' | 'YEAR' | 'NEWEST'

export interface BookSearch {
  q?: string
  categoryId?: number
  availableOnly?: boolean
  sort?: BookSort
  page?: number
  size?: number
}

export interface BookInput {
  isbn?: string | null
  title: string
  author: string
  publisher?: string | null
  publicationYear?: number | null
  description?: string | null
  categoryId: number
}

export interface CategoryInput {
  name: string
  description?: string | null
}

export const catalogueApi = {
  searchBooks: (search: BookSearch) => request<PageResponse<Book>>('/books', { query: { ...search } }),
  getBook: (id: number) => request<Book>(`/books/${id}`),
  createBook: (input: BookInput) => request<Book>('/books', { method: 'POST', body: input }),
  updateBook: (id: number, input: BookInput) => request<Book>(`/books/${id}`, { method: 'PUT', body: input }),
  deleteBook: (id: number) => request<void>(`/books/${id}`, { method: 'DELETE' }),

  listCategories: () => request<Category[]>('/categories'),
  createCategory: (input: CategoryInput) => request<Category>('/categories', { method: 'POST', body: input }),
  updateCategory: (id: number, input: CategoryInput) => request<Category>(`/categories/${id}`, { method: 'PUT', body: input }),
  deleteCategory: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),

  listCopies: (bookId: number) => request<Copy[]>(`/books/${bookId}/copies`),
  addCopy: (bookId: number, input: { barcode: string; shelfLocation?: string | null }) =>
    request<Copy>(`/books/${bookId}/copies`, { method: 'POST', body: input }),
  updateCopy: (id: number, input: { shelfLocation?: string | null; status?: CopyStatus }) =>
    request<Copy>(`/copies/${id}`, { method: 'PATCH', body: input }),
}
