import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { catalogueApi } from '../../api/catalogue'
import { describeError } from '../../api/client'
import type { Category } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { categorySchema, type CategoryForm } from './bookSchema'

/** Add, rename and delete categories. A category that still has books cannot be deleted; the backend says so. */
export function CategoriesModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Category | null>(null)
  const [toDelete, setToDelete] = useState<Category | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogueApi.listCategories })
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CategoryForm>({ resolver: zodResolver(categorySchema), defaultValues: { name: '', description: '' } })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['categories'] })
    void queryClient.invalidateQueries({ queryKey: ['books'] })
    void queryClient.invalidateQueries({ queryKey: ['staff-books'] })
  }

  const save = useMutation({
    mutationFn: (form: CategoryForm) => {
      const input = { name: form.name, description: form.description || null }
      return editing ? catalogueApi.updateCategory(editing.id, input) : catalogueApi.createCategory(input)
    },
    onSuccess: (saved) => {
      toast.success(editing ? `Renamed to “${saved.name}”.` : `Added the category “${saved.name}”.`)
      setEditing(null)
      setFormError(null)
      reset({ name: '', description: '' })
      refresh()
    },
    onError: (error) => setFormError(describeError(error)),
  })

  const remove = useMutation({
    mutationFn: (id: number) => catalogueApi.deleteCategory(id),
    onSuccess: () => {
      toast.success('Category deleted.')
      setToDelete(null)
      refresh()
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToDelete(null)
    },
  })

  function startEditing(category: Category) {
    setEditing(category)
    setFormError(null)
    reset({ name: category.name, description: category.description ?? '' })
  }

  function stopEditing() {
    setEditing(null)
    setFormError(null)
    reset({ name: '', description: '' })
  }

  return (
    <>
      <Modal title="Categories" onClose={onClose}>
        {categories.isPending ? (
          <Skeleton className="h-24" />
        ) : categories.isError ? (
          <ErrorState error={categories.error} onRetry={() => categories.refetch()} />
        ) : (
          <ul className="mb-5 max-h-60 divide-y divide-slate-100 overflow-y-auto rounded-lg ring-1 ring-slate-200">
            {categories.data.length === 0 && <li className="px-3 py-3 text-sm text-slate-500">No categories yet.</li>}
            {categories.data.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                <span className="truncate text-slate-800">{c.name}</span>
                <span className="flex shrink-0 gap-1">
                  <button type="button" onClick={() => startEditing(c)} aria-label={`Rename ${c.name}`} className="rounded p-1 text-slate-500 hover:bg-slate-100">
                    <Pencil className="h-4 w-4" aria-hidden="true" />
                  </button>
                  <button type="button" onClick={() => setToDelete(c)} aria-label={`Delete ${c.name}`} className="rounded p-1 text-slate-500 hover:bg-red-50 hover:text-red-600">
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate className="space-y-3">
          <h3 className="text-sm font-semibold text-slate-800">{editing ? `Rename “${editing.name}”` : 'Add a category'}</h3>
          {formError && <Alert>{formError}</Alert>}
          <TextField id="categoryName" label="Name" error={errors.name?.message} {...register('name')} />
          <TextField id="categoryDescription" label="Description (optional)" error={errors.description?.message} {...register('description')} />
          <div className="flex justify-end gap-2">
            {editing && (
              <Button variant="secondary" onClick={stopEditing}>
                Cancel rename
              </Button>
            )}
            <Button type="submit" loading={save.isPending}>
              {editing ? 'Save' : 'Add category'}
            </Button>
          </div>
        </form>
      </Modal>

      {toDelete && (
        <ConfirmDialog
          title="Delete this category?"
          message={`“${toDelete.name}” will be removed. A category that still has books cannot be deleted.`}
          confirmLabel="Delete category"
          loading={remove.isPending}
          onConfirm={() => remove.mutate(toDelete.id)}
          onCancel={() => setToDelete(null)}
        />
      )}
    </>
  )
}
