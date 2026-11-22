import { ShieldAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-20 text-center">
      <ShieldAlert className="h-10 w-10 text-amber-500" aria-hidden="true" />
      <h1 className="mt-4 text-xl font-semibold text-slate-900">You do not have access to this page</h1>
      <p className="mt-2 max-w-sm text-sm text-slate-500">This area is for a different kind of account.</p>
      <Link to="/" className="mt-6 text-sm font-medium text-brand-700 hover:underline">
        Go to your home page
      </Link>
    </div>
  )
}
