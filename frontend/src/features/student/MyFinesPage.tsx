import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { finesApi } from '../../api/circulation'
import type { FineStatus } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Card } from '../../components/ui/Card'
import { SelectField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDate, formatMoney } from '../../lib/format'

const PAGE_SIZE = 10
const STATUSES: FineStatus[] = ['UNPAID', 'PAID', 'WAIVED']

function parseStatus(value: string | null): FineStatus | undefined {
  return STATUSES.find((s) => s === value)
}

export default function MyFinesPage() {
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)

  const balance = useQuery({ queryKey: ['fine-balance'], queryFn: finesApi.balance })
  const fines = useQuery({
    queryKey: ['my-fines', { status, page }],
    queryFn: () => finesApi.mine({ status, page, size: PAGE_SIZE }),
  })

  return (
    <>
      <PageHeader title="Fines" subtitle="A fine is added when a book is returned late. Pay at the library desk." />

      <Card title="What you owe" className="mb-6">
        {balance.isPending ? (
          <Skeleton className="h-12" />
        ) : balance.isError ? (
          <ErrorState error={balance.error} onRetry={() => balance.refetch()} />
        ) : (
          <div className="space-y-3">
            <p className="text-3xl font-semibold text-slate-900" aria-label={`You owe ${formatMoney(balance.data.unpaid)}`}>
              {formatMoney(balance.data.unpaid)}
            </p>
            {balance.data.blocked ? (
              <Alert>
                You cannot borrow new books until your unpaid fines are {formatMoney(balance.data.borrowingBlockedAbove)} or less. Please
                pay at the desk.
              </Alert>
            ) : balance.data.unpaid > 0 ? (
              <p className="text-sm text-slate-500">
                You can still borrow books. New loans are only blocked when unpaid fines go above{' '}
                {formatMoney(balance.data.borrowingBlockedAbove)}.
              </p>
            ) : (
              <p className="text-sm text-slate-500">You have no unpaid fines.</p>
            )}
          </div>
        )}
      </Card>

      <div className="mb-4 max-w-xs">
        <SelectField
          id="status"
          label="Show"
          value={status ?? ''}
          onChange={(event) => setParams(event.target.value ? { status: event.target.value } : {}, { replace: true })}
        >
          <option value="">All fines</option>
          <option value="UNPAID">Unpaid</option>
          <option value="PAID">Paid</option>
          <option value="WAIVED">Waived</option>
        </SelectField>
      </div>

      <Card className="p-0 [&>div]:p-0">
        {fines.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading fines">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        ) : fines.isError ? (
          <ErrorState error={fines.error} onRetry={() => fines.refetch()} />
        ) : fines.data.content.length === 0 ? (
          <EmptyState title={status ? 'No fines with this status' : 'You have no fines'} message="Returning books on time keeps it that way." />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {fines.data.content.map((fine) => (
                <li key={fine.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{fine.bookTitle}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      Charged on {formatDate(fine.assessedAt)}
                      {fine.paidAt ? ` · paid on ${formatDate(fine.paidAt)}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-semibold text-slate-900">{formatMoney(fine.amount)}</span>
                    <StatusBadge status={fine.status} />
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={fines.data.page}
              totalPages={fines.data.totalPages}
              totalElements={fines.data.totalElements}
              noun="fine"
              onPageChange={(next) => setParams({ ...(status ? { status } : {}), ...(next ? { page: String(next) } : {}) }, { replace: true })}
            />
          </>
        )}
      </Card>
    </>
  )
}
