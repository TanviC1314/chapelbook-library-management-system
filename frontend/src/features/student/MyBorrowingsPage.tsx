import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { loansApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Loan } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { describeDue, formatDate, formatMoney } from '../../lib/format'

const PAGE_SIZE = 10

function when(loan: Loan): string {
  if (loan.status === 'RETURNED') return `Returned on ${formatDate(loan.returnedAt!)}`
  return `Due ${formatDate(loan.dueAt)} (${describeDue(loan.dueAt)})`
}

/** "My Borrowings": what the student has out now, or everything they ever borrowed. */
export default function MyBorrowingsPage() {
  const [params, setParams] = useSearchParams()
  const view = params.get('view') === 'all' ? 'all' : 'active'
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)
  const toast = useToast()
  const queryClient = useQueryClient()

  const loans = useQuery({
    queryKey: ['my-loans', { view, page }],
    queryFn: () => loansApi.mine({ activeOnly: view === 'active', page, size: PAGE_SIZE }),
  })

  const renew = useMutation({
    mutationFn: (id: number) => loansApi.renew(id),
    onSuccess: (loan) => {
      toast.success(`Renewed. The new due date is ${formatDate(loan.dueAt)}.`)
      void queryClient.invalidateQueries({ queryKey: ['my-loans'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) => toast.error(describeError(error)),
  })

  function show(next: 'active' | 'all') {
    setParams(next === 'active' ? {} : { view: next }, { replace: true })
  }

  return (
    <>
      <PageHeader title="My borrowings" subtitle="Books you have out, and everything you have borrowed before." />

      <div role="tablist" aria-label="Which borrowings to show" className="mb-4 inline-flex rounded-lg bg-slate-200 p-1 text-sm">
        {(
          [
            ['active', 'On loan now'],
            ['all', 'All borrowings'],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={view === value}
            onClick={() => show(value)}
            className={`rounded-md px-3 py-1.5 font-medium transition ${view === value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
          >
            {label}
          </button>
        ))}
      </div>

      <Card className="p-0 [&>div]:p-0">
        {loans.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading borrowings">
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        ) : loans.isError ? (
          <ErrorState error={loans.error} onRetry={() => loans.refetch()} />
        ) : loans.data.content.length === 0 ? (
          <EmptyState
            title={view === 'active' ? 'You have no books on loan' : 'You have not borrowed anything yet'}
            message="Find a book in the catalogue, reserve it, and collect it from the desk."
            action={
              <Link to="/catalogue" className="text-sm font-medium text-brand-700 hover:underline">
                Browse the catalogue
              </Link>
            }
          />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {loans.data.content.map((loan) => (
                <li key={loan.id} className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
                  <div className="min-w-0">
                    <Link to={`/catalogue/${loan.bookId}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">
                      {loan.bookTitle}
                    </Link>
                    <p className={`mt-1 text-sm ${loan.status === 'OVERDUE' ? 'font-medium text-red-700' : 'text-slate-600'}`}>{when(loan)}</p>
                    <p className="mt-1 text-xs text-slate-400">
                      Borrowed {formatDate(loan.borrowedAt)} · copy {loan.copyBarcode}
                      {loan.renewalCount > 0 ? ` · renewed ${loan.renewalCount} ${loan.renewalCount === 1 ? 'time' : 'times'}` : ''}
                    </p>
                    {loan.status === 'OVERDUE' && (
                      <p className="mt-2 text-xs text-red-700">Please return this book to the desk. A fine is charged for each day it is late.</p>
                    )}
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusBadge status={loan.status} />
                    {loan.status === 'ACTIVE' && (
                      <Button
                        variant="secondary"
                        loading={renew.isPending && renew.variables === loan.id}
                        onClick={() => renew.mutate(loan.id)}
                        aria-label={`Renew ${loan.bookTitle}`}
                      >
                        Renew
                      </Button>
                    )}
                    {loan.fineAmount != null && <span className="text-sm text-slate-600">Fine {formatMoney(loan.fineAmount)}</span>}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={loans.data.page}
              totalPages={loans.data.totalPages}
              totalElements={loans.data.totalElements}
              noun="borrowing"
              onPageChange={(next) => setParams({ ...(view === 'all' ? { view } : {}), ...(next ? { page: String(next) } : {}) }, { replace: true })}
            />
          </>
        )}
      </Card>
    </>
  )
}
