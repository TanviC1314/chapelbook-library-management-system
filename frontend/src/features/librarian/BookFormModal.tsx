import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { catalogueApi } from '../../api/catalogue'
import { ApiError, describeError } from '../../api/client'
import type { Book, Category } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { bookSchema, toBookInput, type BookForm } from './bookSchema'

interface Props {
  /** The book being edited, or nothing to add a new one. */
  book?: Book
  categories: Category[]
  onClose: () => void
}

export function BookFormModal({ book, categories, onClose }: Props) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<BookForm>({
    resolver: zodResolver(bookSchema),
    defaultValues: {
      title: book?.title ?? '',
      author: book?.author ?? '',
      isbn: book?.isbn ?? '',
      publisher: book?.publisher ?? '',
      publicationYear: book?.publicationYear ? String(book.publicationYear) : '',
      description: book?.description ?? '',
      categoryId: book ? String(book.category.id) : '',
    },
  })

  const save = useMutation({
    mutationFn: (form: BookForm) => (book ? catalogueApi.updateBook(book.id, toBookInput(form)) : catalogueApi.createBook(toBookInput(form))),
    onSuccess: (saved) => {
      toast.success(book ? `Saved “${saved.title}”.` : `Added “${saved.title}”. Now add its copies.`)
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['book'] })
      void queryClient.invalidateQueries({ queryKey: ['staff-books'] })
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        for (const { field, message } of error.fieldErrors) {
          if (field in bookSchema.shape) setError(field as keyof BookForm, { message })
        }
      }
      setFormError(describeError(error))
    },
  })

  return (
    <Modal title={book ? 'Edit book' : 'Add a book'} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="title" label="Title" error={errors.title?.message} {...register('title')} />
        <TextField id="author" label="Author" error={errors.author?.message} {...register('author')} />
        <SelectField id="categoryId" label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
          <option value="">Choose a category</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField id="isbn" label="ISBN (optional)" error={errors.isbn?.message} {...register('isbn')} />
          <TextField id="publicationYear" label="Year (optional)" inputMode="numeric" error={errors.publicationYear?.message} {...register('publicationYear')} />
        </div>
        <TextField id="publisher" label="Publisher (optional)" error={errors.publisher?.message} {...register('publisher')} />
        <TextAreaField id="description" label="Description (optional)" error={errors.description?.message} {...register('description')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {book ? 'Save changes' : 'Add book'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
