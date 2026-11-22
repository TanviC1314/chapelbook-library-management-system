import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { FullPageSpinner } from '../components/ui/Spinner'
import ForbiddenPage from '../pages/ForbiddenPage'
import { useAuth } from './AuthContext'
import { homeFor } from './roles'

/** Pages that need a signed-in user; everyone else is sent to the login page. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageSpinner />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Login and registration pages; a signed-in user has no business there. */
export function PublicOnlyRoute() {
  const { status, user } = useAuth()
  if (status === 'loading') return <FullPageSpinner />
  if (status === 'authenticated' && user) return <Navigate to={homeFor(user.role)} replace />
  return <Outlet />
}

/**
 * Pages for specific roles. This only decides what the interface shows: the backend checks the role on every
 * request regardless, so hiding a page here is a courtesy and never the security boundary.
 */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useAuth()
  if (!user || !roles.includes(user.role)) return <ForbiddenPage />
  return <Outlet />
}

/** "/" sends each person to their own portal. */
export function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homeFor(user.role) : '/login'} replace />
}
