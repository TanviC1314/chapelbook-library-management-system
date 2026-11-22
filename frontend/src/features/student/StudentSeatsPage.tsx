import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plug } from 'lucide-react'
import { useMemo, useState } from 'react'
import { describeError } from '../../api/client'
import { seatsApi } from '../../api/seats'
import type { Seat, SeatBooking } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { SelectField, TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatTime } from '../../lib/format'
import { checkSeatWindow, defaultWindow, MAX_DAYS_AHEAD, MAX_HOURS, MIN_MINUTES } from '../../lib/seatWindow'

function describeBooking(b: SeatBooking): string {
  return `${formatDate(b.startsAt)}, ${formatTime(b.startsAt)} to ${formatTime(b.endsAt)}`
}

export default function StudentSeatsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const initial = useMemo(() => defaultWindow(), [])
  const [start, setStart] = useState(initial.start)
  const [end, setEnd] = useState(initial.end)
  const [zone, setZone] = useState('')
  const [toCancel, setToCancel] = useState<SeatBooking | null>(null)

  const check = checkSeatWindow(start, end)
  const slot = check.error === null ? { from: check.startsAt!, to: check.endsAt! } : null

  // Which seats are free for the whole chosen window, asked of the backend once the window is valid.
  const seats = useQuery({
    queryKey: ['seats', slot],
    queryFn: () => seatsApi.list(slot ?? {}),
    enabled: slot !== null,
  })
  const bookings = useQuery({
    queryKey: ['my-seat-bookings'],
    queryFn: () => seatsApi.mine({ upcomingOnly: true, size: 20 }),
  })

  const zones = useMemo(() => [...new Set((seats.data ?? []).map((s) => s.zone))].sort(), [seats.data])
  const visible = (seats.data ?? []).filter((s) => !zone || s.zone === zone)

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['seats'] })
    void queryClient.invalidateQueries({ queryKey: ['my-seat-bookings'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const book = useMutation({
    mutationFn: (seat: Seat) => seatsApi.book({ seatId: seat.id, startsAt: slot!.from, endsAt: slot!.to }),
    onSuccess: (booking) => {
      toast.success(`Seat ${booking.seatLabel} booked: ${describeBooking(booking)}.`)
      refresh()
    },
    onError: (error) => {
      toast.error(describeError(error))
      refresh() // somebody else may have just taken it: show the current picture
    },
  })

  const cancel = useMutation({
    mutationFn: (id: number) => seatsApi.cancel(id),
    onSuccess: () => {
      toast.success('Booking cancelled.')
      setToCancel(null)
      refresh()
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToCancel(null)
      refresh()
    },
  })

  return (
    <>
      <PageHeader title="Study seats" subtitle="Pick a time, see which seats are free, and book one." />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="When do you need a seat?">
            <div className="grid gap-4 sm:grid-cols-3">
              <TextField id="start" label="Start" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
              <TextField id="end" label="End" type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} />
              <SelectField id="zone" label="Zone" value={zone} onChange={(e) => setZone(e.target.value)}>
                <option value="">All zones</option>
                {zones.map((z) => (
                  <option key={z} value={z}>
                    {z}
                  </option>
                ))}
              </SelectField>
            </div>
            <p className="mt-3 text-xs text-slate-500">
              Bookings last {MIN_MINUTES} minutes to {MAX_HOURS} hours and can be made up to {MAX_DAYS_AHEAD} days ahead.
            </p>
            {check.error && (
              <div className="mt-3">
                <Alert>{check.error}</Alert>
              </div>
            )}
          </Card>

          {check.error ? null : seats.isPending ? (
            <div className="grid gap-4 sm:grid-cols-2" aria-busy="true" aria-label="Loading seats">
              <Skeleton className="h-28" />
              <Skeleton className="h-28" />
            </div>
          ) : seats.isError ? (
            <Card>
              <ErrorState error={seats.error} onRetry={() => seats.refetch()} />
            </Card>
          ) : visible.length === 0 ? (
            <Card>
              <EmptyState title="No seats to show" message={zone ? 'There are no seats in this zone.' : 'There are no study seats yet.'} />
            </Card>
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2">
              {visible.map((seat) => (
                <li key={seat.id} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold text-slate-900">Seat {seat.label}</p>
                      <p className="text-sm text-slate-500">{seat.zone}</p>
                    </div>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${
                        seat.available ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' : 'bg-slate-100 text-slate-600 ring-slate-500/20'
                      }`}
                    >
                      {seat.available ? 'Free' : 'Taken'}
                    </span>
                  </div>
                  {seat.hasPower && (
                    <p className="mt-2 flex items-center gap-1 text-xs text-slate-500">
                      <Plug className="h-3.5 w-3.5" aria-hidden="true" />
                      Power socket
                    </p>
                  )}
                  <Button
                    className="mt-4 w-full"
                    disabled={!seat.available}
                    loading={book.isPending && book.variables?.id === seat.id}
                    onClick={() => book.mutate(seat)}
                    aria-label={`Book seat ${seat.label}`}
                  >
                    {seat.available ? 'Book this seat' : 'Not free then'}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <Card title="My upcoming bookings">
          {bookings.isPending ? (
            <Skeleton className="h-16" />
          ) : bookings.isError ? (
            <ErrorState error={bookings.error} onRetry={() => bookings.refetch()} />
          ) : bookings.data.content.length === 0 ? (
            <p className="text-sm text-slate-500">You have no upcoming seat bookings.</p>
          ) : (
            <ul className="space-y-4">
              {bookings.data.content.map((b) => (
                <li key={b.id} className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium text-slate-900">
                      Seat {b.seatLabel} · {b.zone}
                    </p>
                    <p className="text-xs text-slate-500">{describeBooking(b)}</p>
                    <div className="mt-1">
                      <StatusBadge status={b.status} />
                    </div>
                  </div>
                  <Button variant="secondary" onClick={() => setToCancel(b)} aria-label={`Cancel booking for seat ${b.seatLabel}`}>
                    Cancel
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {toCancel && (
        <ConfirmDialog
          title="Cancel this booking?"
          message={`Seat ${toCancel.seatLabel} on ${describeBooking(toCancel)} will be released for other students.`}
          confirmLabel="Cancel booking"
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}
