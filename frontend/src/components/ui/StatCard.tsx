import { Link } from 'react-router-dom'

type Tone = 'default' | 'warning' | 'danger'

const tones: Record<Tone, string> = {
  default: 'text-slate-900',
  warning: 'text-amber-700',
  danger: 'text-red-700',
}

interface Props {
  label: string
  value: string | number
  hint?: string
  tone?: Tone
  /** Makes the whole card a link to where the numbers come from. */
  to?: string
}

/** One headline number with a label, used on the librarian and admin dashboards. */
export function StatCard({ label, value, hint, tone = 'default', to }: Props) {
  const body = (
    <>
      <p className="text-sm font-medium text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tracking-tight ${tones[tone]}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </>
  )
  const classes = 'block rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200'
  return to ? (
    <Link to={to} className={`${classes} transition hover:ring-brand-500`}>
      {body}
    </Link>
  ) : (
    <div className={classes}>{body}</div>
  )
}
