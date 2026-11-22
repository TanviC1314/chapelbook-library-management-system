import { describe, expect, it } from 'vitest'
import { bookSchema, categorySchema, toBookInput, type BookForm } from './bookSchema'

const valid: BookForm = {
  title: 'Clean Code',
  author: 'Robert C. Martin',
  isbn: '978-0-13-235088-4',
  publisher: 'Prentice Hall',
  publicationYear: '2008',
  description: 'A handbook.',
  categoryId: '3',
}

function problems(input: unknown): Record<string, string> {
  const result = bookSchema.safeParse(input)
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('bookSchema', () => {
  it('accepts a complete book', () => {
    expect(bookSchema.safeParse(valid).success).toBe(true)
  })

  it('accepts a book with only the required fields', () => {
    expect(bookSchema.safeParse({ ...valid, isbn: '', publisher: '', publicationYear: '', description: '' }).success).toBe(true)
  })

  it.each([
    [{ title: '   ' }, 'title', 'Title is required'],
    [{ author: '' }, 'author', 'Author is required'],
    [{ categoryId: '' }, 'categoryId', 'Choose a category'],
    [{ isbn: 'abc' }, 'isbn', 'An ISBN uses digits, X, spaces and hyphens (10 to 20 characters)'],
    [{ publicationYear: '99' }, 'publicationYear', 'Enter a four digit year between 1000 and 2200'],
    [{ publicationYear: '2300' }, 'publicationYear', 'Enter a four digit year between 1000 and 2200'],
    [{ publicationYear: 'abcd' }, 'publicationYear', 'Enter a four digit year between 1000 and 2200'],
    [{ title: 'x'.repeat(301) }, 'title', 'Title must be at most 300 characters'],
    [{ description: 'x'.repeat(2001) }, 'description', 'Description must be at most 2000 characters'],
  ])('rejects %j', (override, field, message) => {
    expect(problems({ ...valid, ...override })[field]).toBe(message)
  })

  it('accepts the year limits themselves', () => {
    expect(bookSchema.safeParse({ ...valid, publicationYear: '1000' }).success).toBe(true)
    expect(bookSchema.safeParse({ ...valid, publicationYear: '2200' }).success).toBe(true)
  })
})

describe('toBookInput', () => {
  it('converts the form strings to what the API expects', () => {
    expect(toBookInput(valid)).toEqual({
      title: 'Clean Code',
      author: 'Robert C. Martin',
      isbn: '978-0-13-235088-4',
      publisher: 'Prentice Hall',
      publicationYear: 2008,
      description: 'A handbook.',
      categoryId: 3,
    })
  })

  it('sends null, not an empty string, for anything left blank', () => {
    expect(toBookInput({ ...valid, isbn: '', publisher: '', publicationYear: '', description: '' })).toMatchObject({
      isbn: null,
      publisher: null,
      publicationYear: null,
      description: null,
    })
  })
})

describe('categorySchema', () => {
  it('needs a name', () => {
    expect(categorySchema.safeParse({ name: ' ', description: '' }).success).toBe(false)
    expect(categorySchema.safeParse({ name: 'History', description: '' }).success).toBe(true)
  })

  it('limits the lengths', () => {
    expect(categorySchema.safeParse({ name: 'x'.repeat(101), description: '' }).success).toBe(false)
    expect(categorySchema.safeParse({ name: 'ok', description: 'x'.repeat(501) }).success).toBe(false)
  })
})
