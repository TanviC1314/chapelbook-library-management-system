import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, describeError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { homeFor } from '../../auth/roles'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { AuthShell } from './AuthShell'
import { registerSchema, type RegisterForm } from './schemas'

export default function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema), defaultValues: { studentNumber: '' } })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const user = await registerAccount({
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        // Registration always creates a student; there is deliberately no role to choose.
        studentNumber: values.studentNumber || undefined,
      })
      navigate(homeFor(user.role), { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        for (const { field, message } of error.fieldErrors) {
          if (field in values) setError(field as keyof RegisterForm, { message })
        }
      }
      setFormError(describeError(error))
    }
  })

  return (
    <AuthShell title="Create your account" subtitle="Students can register here. Librarians and admins are added by an administrator.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="fullName" label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <TextField id="email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          id="studentNumber"
          label="Student number (optional)"
          error={errors.studentNumber?.message}
          {...register('studentNumber')}
        />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          id="confirmPassword"
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
