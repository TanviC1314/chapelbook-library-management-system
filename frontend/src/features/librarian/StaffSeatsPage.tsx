import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plug } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { describeError } from '../../api/client'
import { seatsApi } from '../../api/seats'
import type { Seat, SeatBooking } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { SelectField, TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatTime } from '../../lib/format'

const seatSchema = z.object({
  label: z.string().trim().min(1, 'Label is required').max(50, 'Label must be at most 50 characters'),
  zone: z.string().trim().min(1, 'Zone is required').max(50, 'Zone must be at most 50 characters'),
  hasPower: z.boolean(),
  status: z.enum(['ACTIVE', 'OUT_OF_SERVICE']),
})
type SeatForm = z.infer<typeof seatSchema>

/** Midnight at the start of a local day as an ISO instant, for asking the backend about whole days. */
function startOfDay(date: string): string {
  return new Date(`${date}T00:00:00`).toISOString()
}

function today(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00`)
  d.setDate(d.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Staff: look after the seats themselves, and watch who has booked them. */
export default function StaffSeatsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<{ seat?: Seat } | null>(null)
  const [from, setFrom] = useState(() => today())
  const [to, setTo] = useState(() => addDays(today(), 6))
  const [toCancel, setToCancel] = useState<SeatBooking | null>(null)

  const rangeOk = from !== '' && to !== '' && to >= from
  const window = useMemo(() => (rangeOk ? { from: startOfDay(from), to: startOfDay(addDays(to, 1)) } : null), [from, to, rangeOk])

  const seats = useQuery({ queryKey: ['staff-seats'], queryFn: () => seatsApi.list() })
  const bookings = useQuery({
    queryKey: ['staff-seat-bookings', window],
    queryFn: () => seatsApi.listBookings({ ...window!, size: 100 }),
    enabled: window !== null,
  })

  const cancel = useMutation({
    mutationFn: (id: number) => seatsApi.cancel(id),
    onSuccess: () => {
      toast.success('Booking cancelled.')
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-seat-bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-seat-bookings'] })
    },
  })

  return (
    <>
      <PageHeader title="Seats" subtitle="Manage the study seats and watch the bookings." actions={<Button onClick={() => setForm({})}>Add a seat</Button>} />

      <Card title="All seats" className="mb-6 p-0 [&>div]:p-0">
        {seats.isPending ? (
          <div className="p-5" aria-busy="true" aria-label="Loading seats">
            <Skeleton className="h-16" />
          </div>
        ) : seats.isError ? (
          <ErrorState error={seats.error} onRetry={() => seats.refetch()} />
        ) : seats.data.length === 0 ? (
          <EmptyState title="There are no seats yet" message="Add the first study seat." action={<Button onClick={() => setForm({})}>Add a seat</Button>} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[32rem] text-left text-sm">
              <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-medium">Seat</th>
                  <th scope="col" className="px-3 py-3 font-medium">Zone</th>
                  <th scope="col" className="px-3 py-3 font-medium">Status</th>
                  <th scope="col" className="px-5 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {seats.data.map((seat) => (
                  <tr key={seat.id}>
                    <td className="px-5 py-3 font-medium text-slate-900">
                      {seat.label}
                      {seat.hasPower && <Plug className="ml-2 inline h-3.5 w-3.5 text-slate-400" aria-label="Has a power socket" />}
                    </td>
                    <td className="px-3 py-3 text-slate-600">{seat.zone}</td>
                    <td className="px-3 py-3">
                      <StatusBadge status={seat.status} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Button variant="secondary" onClick={() => setForm({ seat })} aria-label={`Edit seat ${seat.label}`}>
                        Edit
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Bookings">
        <div className="mb-4 grid max-w-md gap-4 sm:grid-cols-2">
          <TextField id="from" label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField id="to" label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        {!rangeOk ? (
          <Alert>Choose a start date, and an end date that is not before it.</Alert>
        ) : bookings.isPending ? (
          <Skeleton className="h-20" />
        ) : bookings.isError ? (
          <ErrorState error={bookings.error} onRetry={() => bookings.refetch()} />
        ) : bookings.data.content.length === 0 ? (
          <p className="text-sm text-slate-500">No seats are booked in this period.</p>
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {bookings.data.content.map((b) => (
                <li key={b.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">
                      Seat {b.seatLabel} · {b.zone}
                    </p>
                    <p className="text-sm text-slate-600">
                      {b.userName} · {formatDate(b.startsAt)}, {formatTime(b.startsAt)} to {formatTime(b.endsAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusBadge status={b.status} />
                    <Button variant="secondary" onClick={() => setToCancel(b)} aria-label={`Cancel ${b.userName}'s booking of seat ${b.seatLabel}`}>
                      Cancel
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            {bookings.data.totalElements > bookings.data.content.length && (
              <p className="mt-3 text-sm text-slate-500">Showing the first {bookings.data.content.length} of {bookings.data.totalElements} bookings. Narrow the dates to see the rest.</p>
            )}
          </>
        )}
      </Card>

      {form && <SeatFormModal seat={form.seat} onClose={() => setForm(null)} />}
      {toCancel && (
        <ConfirmDialog
          title="Cancel this booking?"
          message={`${toCancel.userName}'s booking of seat ${toCancel.seatLabel} on ${formatDate(toCancel.startsAt)} will be cancelled and the seat released.`}
          confirmLabel="Cancel booking"
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}

function SeatFormModal({ seat, onClose }: { seat?: Seat; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SeatForm>({
    resolver: zodResolver(seatSchema),
    defaultValues: { label: seat?.label ?? '', zone: seat?.zone ?? '', hasPower: seat?.hasPower ?? false, status: seat?.status ?? 'ACTIVE' },
  })

  const save = useMutation({
    mutationFn: (values: SeatForm) => (seat ? seatsApi.update(seat.id, values) : seatsApi.create(values)),
    onSuccess: (saved) => {
      toast.success(seat ? `Seat ${saved.label} saved.` : `Seat ${saved.label} added.`)
      void queryClient.invalidateQueries({ queryKey: ['staff-seats'] })
      void queryClient.invalidateQueries({ queryKey: ['seats'] })
      onClose()
    },
    onError: (error) => setFormError(describeError(error)),
  })

  return (
    <Modal title={seat ? `Edit seat ${seat.label}` : 'Add a seat'} onClose={onClose}>
      <form onSubmit={handleSubmit((values) => save.mutate(values))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="label" label="Label" hint="What is written on the seat, e.g. Q-01." error={errors.label?.message} {...register('label')} />
        <TextField id="zone" label="Zone" hint="e.g. Quiet or Group." error={errors.zone?.message} {...register('zone')} />
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600" {...register('hasPower')} />
          Has a power socket
        </label>
        <SelectField id="status" label="Status" error={errors.status?.message} {...register('status')}>
          <option value="ACTIVE">In service</option>
          <option value="OUT_OF_SERVICE">Out of service</option>
        </SelectField>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {seat ? 'Save changes' : 'Add seat'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
