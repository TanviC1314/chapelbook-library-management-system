import { BookOpen, LogOut, Menu, X } from 'lucide-react'
import { Suspense, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser, useAuth } from '../../auth/AuthContext'
import { roleLabel } from '../../auth/roles'
import { Spinner } from '../ui/Spinner'
import { navFor } from './navigation'

export default function AppLayout() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const items = navFor(user.role)

  // Close the mobile menu whenever the page changes.
  useEffect(() => setMenuOpen(false), [location.pathname])

  return (
    <div className="min-h-screen lg:flex">
      <header className="sticky top-0 z-30 flex items-center justify-between bg-brand-800 px-4 py-3 text-white lg:hidden">
        <div className="flex items-center gap-2 font-semibold">
          <BookOpen className="h-5 w-5" aria-hidden="true" />
          ChapelBook
        </div>
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={menuOpen}
          aria-controls="main-nav"
          className="rounded p-1 hover:bg-white/10"
        >
          {menuOpen ? <X className="h-6 w-6" aria-hidden="true" /> : <Menu className="h-6 w-6" aria-hidden="true" />}
        </button>
      </header>

      <aside
        id="main-nav"
        className={`${menuOpen ? 'block' : 'hidden'} bg-brand-800 text-brand-100 lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-64 lg:shrink-0 lg:flex-col`}
      >
        <div className="hidden items-center gap-2 px-6 py-6 text-lg font-semibold text-white lg:flex">
          <BookOpen className="h-6 w-6" aria-hidden="true" />
          ChapelBook
        </div>
        <nav aria-label="Main" className="flex-1 space-y-1 overflow-y-auto px-3 py-2">
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-white/15 text-white' : 'hover:bg-white/10 hover:text-white'
                }`
              }
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/10 px-4 py-4">
          <p className="truncate text-sm font-medium text-white">{user.fullName}</p>
          <p className="text-xs text-brand-100">{roleLabel[user.role]}</p>
          <button
            type="button"
            onClick={logout}
            className="mt-3 flex items-center gap-2 text-sm text-brand-100 hover:text-white"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sign out
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 px-4 py-6 sm:px-8 lg:py-8">
        <div className="mx-auto max-w-6xl">
          <Suspense
            fallback={
              <div className="flex justify-center py-20" role="status" aria-label="Loading">
                <Spinner className="h-8 w-8" />
              </div>
            }
          >
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  )
}
