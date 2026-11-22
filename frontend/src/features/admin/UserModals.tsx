import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { usersApi } from '../../api/admin'
import { ApiError, describeError } from '../../api/client'
import type { User } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import {
  changesFrom,
  createUserSchema,
  editUserSchema,
  resetPasswordSchema,
  toCreateUserInput,
  type CreateUserForm,
  type EditUserForm,
  type ResetPasswordForm,
} from './userSchemas'

const CREATE_FIELDS = ['fullName', 'email', 'role', 'studentNumber', 'password']

function useRefreshUsers() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

export function CreateUserModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshUsers()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors },
  } = useForm<CreateUserForm>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { fullName: '', email: '', role: 'LIBRARIAN', studentNumber: '', password: '' },
  })
  const role = watch('role')

  const create = useMutation({
    mutationFn: (form: CreateUserForm) => usersApi.create(toCreateUserInput(form)),
    onSuccess: (user) => {
      toast.success(`Account created for ${user.fullName}.`)
      refresh()
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        for (const { field, message } of error.fieldErrors) {
          if (CREATE_FIELDS.includes(field)) setError(field as keyof CreateUserForm, { message })
        }
      }
      setFormError(describeError(error))
    },
  })

  return (
    <Modal title="Add an account" onClose={onClose}>
      <form onSubmit={handleSubmit((form) => create.mutate(form))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="create-fullName" label="Full name" error={errors.fullName?.message} {...register('fullName')} />
        <TextField id="create-email" label="Email" type="email" autoComplete="off" error={errors.email?.message} {...register('email')} />
        <SelectField id="create-role" label="Role" error={errors.role?.message} {...register('role')}>
          <option value="LIBRARIAN">Librarian</option>
          <option value="ADMIN">Administrator</option>
          <option value="STUDENT">Student</option>
        </SelectField>
        {role === 'STUDENT' && (
          <TextField id="create-studentNumber" label="Student number (optional)" error={errors.studentNumber?.message} {...register('studentNumber')} />
        )}
        <TextField
          id="create-password"
          label="Temporary password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters. Tell the person to change it after signing in."
          error={errors.password?.message}
          {...register('password')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create account
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Rename, activate or deactivate, and move a staff account between librarian and administrator. A student always
 * stays a student, and nobody can deactivate or demote their own account (the backend enforces both rules too).
 */
export function EditUserModal({ user, isSelf, onClose }: { user: User; isSelf: boolean; onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshUsers()
  const [formError, setFormError] = useState<string | null>(null)
  const isStudent = user.role === 'STUDENT'
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EditUserForm>({
    resolver: zodResolver(editUserSchema),
    defaultValues: { fullName: user.fullName, role: user.role, active: user.active },
  })

  const save = useMutation({
    mutationFn: (form: EditUserForm) => usersApi.update(user.id, changesFrom(user, form)),
    onSuccess: (saved) => {
      toast.success(`Saved ${saved.fullName}.`)
      refresh()
      onClose()
    },
    onError: (error) => setFormError(describeError(error)),
  })

  return (
    <Modal title={`Edit ${user.fullName}`} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <p className="text-sm text-slate-500">{user.email}</p>
        <TextField id="edit-fullName" label="Full name" error={errors.fullName?.message} {...register('fullName')} />
        <SelectField
          id="edit-role"
          label="Role"
          disabled={isStudent || isSelf}
          hint={isStudent ? 'Student accounts stay students.' : isSelf ? 'You cannot change your own role.' : undefined}
          {...register('role')}
        >
          {isStudent ? (
            <option value="STUDENT">Student</option>
          ) : (
            <>
              <option value="LIBRARIAN">Librarian</option>
              <option value="ADMIN">Administrator</option>
            </>
          )}
        </SelectField>
        <div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" disabled={isSelf} className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600" {...register('active')} />
            Account is active
          </label>
          <p className="mt-1 text-xs text-slate-500">
            {isSelf ? 'You cannot deactivate your own account.' : 'A deactivated person is signed out on their next request and cannot sign in.'}
          </p>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save changes
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export function ResetPasswordModal({ user, onClose }: { user: User; onClose: () => void }) {
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordForm>({ resolver: zodResolver(resetPasswordSchema), defaultValues: { newPassword: '', confirmPassword: '' } })

  const reset = useMutation({
    mutationFn: (form: ResetPasswordForm) => usersApi.resetPassword(user.id, form.newPassword),
    onSuccess: () => {
      toast.success(`Password changed for ${user.fullName}.`)
      onClose()
    },
    onError: (error) => setFormError(describeError(error)),
  })

  return (
    <Modal title={`Reset password for ${user.fullName}`} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => reset.mutate(form))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <p className="text-sm text-slate-500">
          They can sign in with the new password straight away. A session they already have open keeps working until it expires within the hour; deactivate
          the account instead if you need to cut access immediately.
        </p>
        <TextField id="reset-newPassword" label="New password" type="password" autoComplete="new-password" error={errors.newPassword?.message} {...register('newPassword')} />
        <TextField
          id="reset-confirmPassword"
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={reset.isPending}>
            Change password
          </Button>
        </div>
      </form>
    </Modal>
  )
}
