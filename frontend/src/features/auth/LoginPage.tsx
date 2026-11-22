import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, describeError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { homeFor } from '../../auth/roles'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { AuthShell } from './AuthShell'
import { loginSchema, type LoginForm } from './schemas'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const user = await login(values.email.trim(), values.password)
      // Return to the page they were trying to open if they have access to it; otherwise their own portal.
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : homeFor(user.role), { replace: true })
    } catch (error) {
      setFormError(error instanceof ApiError && error.status === 401 ? 'Invalid email or password.' : describeError(error))
    }
  })

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to browse books, manage loans and book a study seat.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        New student?{' '}
        <Link to="/register" className="font-medium text-brand-700 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthShell>
  )
}
