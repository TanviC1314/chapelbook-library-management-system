import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { finesApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Fine, FineStatus } from '../../api/types'
import { useCurrentUser } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { SelectField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatMoney } from '../../lib/format'

const PAGE_SIZE = 15
const STATUSES: FineStatus[] = ['UNPAID', 'PAID', 'WAIVED']

type Action = { kind: 'pay' | 'waive'; fine: Fine }

/**
 * Fines owed to the library. A librarian records that a student paid at the desk (the app does not take card
 * payments); only an administrator can waive a fine.
 */
export default function StaffFinesPage() {
  const user = useCurrentUser()
  const [params, setParams] = useSearchParams()
  // Unpaid fines are what staff come here for, so that is the default view.
  const requested = params.get('status')
  const status: FineStatus | undefined = requested === 'ALL' ? undefined : (STATUSES.find((s) => s === requested) ?? 'UNPAID')
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [action, setAction] = useState<Action | null>(null)

  const fines = useQuery({
    queryKey: ['staff-fines', { status, page }],
    queryFn: () => finesApi.list({ status, page, size: PAGE_SIZE }),
  })

  const settle = useMutation({
    mutationFn: ({ kind, fine }: Action) => (kind === 'pay' ? finesApi.pay(fine.id) : finesApi.waive(fine.id)),
    onSuccess: (fine, { kind }) => {
      toast.success(kind === 'pay' ? `Payment of ${formatMoney(fine.amount)} from ${fine.userName} recorded.` : `The fine for ${fine.userName} was waived.`)
      setAction(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-fines'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) => {
      toast.error(describeError(error))
      setAction(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-fines'] })
    },
  })

  return (
    <>
      <PageHeader title="Fines" subtitle="Fines raised when books come back late." />

      <div className="mb-4 max-w-xs">
        <SelectField
          id="status"
          label="Show"
          value={status ?? 'ALL'}
          onChange={(e) => setParams(e.target.value === 'UNPAID' ? {} : { status: e.target.value }, { replace: true })}
        >
          <option value="UNPAID">Unpaid</option>
          <option value="PAID">Paid</option>
          <option value="WAIVED">Waived</option>
          <option value="ALL">All fines</option>
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
          <EmptyState title={status === 'UNPAID' ? 'No unpaid fines' : 'No fines'} message={status === 'UNPAID' ? 'Everyone is up to date.' : 'Nothing has this status.'} />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {fines.data.content.map((fine) => (
                <li key={fine.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{fine.userName}</p>
                    <p className="text-sm text-slate-600">
                      {fine.bookTitle} · charged {formatDate(fine.assessedAt)}
                      {fine.paidAt ? ` · paid ${formatDate(fine.paidAt)}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-semibold text-slate-900">{formatMoney(fine.amount)}</span>
                    <StatusBadge status={fine.status} />
                    {fine.status === 'UNPAID' && (
                      <>
                        <Button onClick={() => setAction({ kind: 'pay', fine })} aria-label={`Record payment from ${fine.userName}`}>
                          Record payment
                        </Button>
                        {user.role === 'ADMIN' && (
                          <Button variant="secondary" onClick={() => setAction({ kind: 'waive', fine })} aria-label={`Waive the fine for ${fine.userName}`}>
                            Waive
                          </Button>
                        )}
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={fines.data.page}
              totalPages={fines.data.totalPages}
              totalElements={fines.data.totalElements}
              noun="fine"
              onPageChange={(next) => setParams({ ...(requested ? { status: requested } : {}), ...(next ? { page: String(next) } : {}) }, { replace: true })}
            />
          </>
        )}
      </Card>

      {action && (
        <ConfirmDialog
          title={action.kind === 'pay' ? 'Record this payment?' : 'Waive this fine?'}
          message={
            action.kind === 'pay'
              ? `Confirm that ${action.fine.userName} has paid ${formatMoney(action.fine.amount)} in full at the desk.`
              : `${action.fine.userName} will no longer owe ${formatMoney(action.fine.amount)}. This cannot be undone.`
          }
          confirmLabel={action.kind === 'pay' ? 'Record payment' : 'Waive fine'}
          loading={settle.isPending}
          onConfirm={() => settle.mutate(action)}
          onCancel={() => setAction(null)}
        />
      )}
    </>
  )
}
