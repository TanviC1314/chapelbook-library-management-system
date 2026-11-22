import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-20 text-center">
      <p className="text-5xl font-semibold text-brand-700">404</p>
      <h1 className="mt-4 text-xl font-semibold text-slate-900">Page not found</h1>
      <p className="mt-2 max-w-sm text-sm text-slate-500">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="mt-6 text-sm font-medium text-brand-700 hover:underline">
        Go to your home page
      </Link>
    </div>
  )
}
