import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { usersApi } from '../../api/admin'
import { loansApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Loan, User } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatMoney } from '../../lib/format'
import { useDebouncedValue } from '../../lib/useDebouncedValue'

/** The desk: lend a copy to a student, and take copies back. Built for a barcode scanner, which types a barcode and presses Enter. */
export default function DeskPage() {
  return (
    <>
      <PageHeader title="Issue and return" subtitle="Scan or type a barcode, then press Enter." />
      <div className="grid gap-6 lg:grid-cols-2">
        <IssueCard />
        <ReturnCard />
      </div>
    </>
  )
}

function useRefreshAfterDeskAction() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of ['dashboard', 'staff-books', 'copies', 'book', 'books', 'staff-reservations', 'staff-fines', 'overdue']) {
      void queryClient.invalidateQueries({ queryKey: [key] })
    }
  }
}

function IssueCard() {
  const refresh = useRefreshAfterDeskAction()
  const barcodeRef = useRef<HTMLInputElement>(null)
  const [search, setSearch] = useState('')
  const [student, setStudent] = useState<User | null>(null)
  const [barcode, setBarcode] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const [done, setDone] = useState<Loan | null>(null)
  const debounced = useDebouncedValue(search.trim(), 300)

  const students = useQuery({
    queryKey: ['student-lookup', debounced],
    queryFn: () => usersApi.list({ q: debounced, role: 'STUDENT', active: true, size: 6 }),
    enabled: debounced.length >= 2 && !student,
  })

  const issue = useMutation({
    mutationFn: () => loansApi.issue({ userId: student!.id, copyBarcode: barcode.trim() }),
    onSuccess: (loan) => {
      setDone(loan)
      setProblem(null)
      setBarcode('')
      refresh()
      barcodeRef.current?.focus() // ready for the next book
    },
    onError: (error) => {
      setDone(null)
      setProblem(describeError(error))
      barcodeRef.current?.select()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setDone(null)
    if (!student) return setProblem('Choose the student first.')
    if (!barcode.trim()) return setProblem('Scan or type the barcode of the book.')
    setProblem(null)
    issue.mutate()
  }

  return (
    <Card title="Issue a book">
      <form onSubmit={submit} noValidate className="space-y-4">
        {student ? (
          <div className="flex items-center justify-between gap-3 rounded-lg bg-brand-50 px-3 py-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{student.fullName}</p>
              <p className="truncate text-xs text-slate-500">
                {student.email}
                {student.studentNumber ? ` · ${student.studentNumber}` : ''}
              </p>
            </div>
            <Button
              variant="ghost"
              onClick={() => {
                setStudent(null)
                setDone(null)
                setProblem(null)
              }}
            >
              Change student
            </Button>
          </div>
        ) : (
          <div>
            <TextField
              id="student-search"
              label="Student"
              type="search"
              placeholder="Name, email or student number"
              autoComplete="off"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {debounced.length >= 2 && (
              <div className="mt-2 rounded-lg ring-1 ring-slate-200">
                {students.isPending ? (
                  <div className="flex justify-center py-4" role="status" aria-label="Searching students">
                    <Spinner />
                  </div>
                ) : students.isError ? (
                  <p role="alert" className="px-3 py-3 text-sm text-red-700">
                    {describeError(students.error)}
                  </p>
                ) : students.data.content.length === 0 ? (
                  <p className="px-3 py-3 text-sm text-slate-500">No active student matches “{debounced}”.</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {students.data.content.map((s) => (
                      <li key={s.id}>
                        <button type="button" onClick={() => setStudent(s)} className="block w-full px-3 py-2 text-left hover:bg-slate-50" aria-label={`Select ${s.fullName}`}>
                          <span className="block text-sm font-medium text-slate-900">{s.fullName}</span>
                          <span className="block text-xs text-slate-500">
                            {s.email}
                            {s.studentNumber ? ` · ${s.studentNumber}` : ''}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        )}

        <TextField
          ref={barcodeRef}
          id="issue-barcode"
          label="Barcode of the book to lend"
          autoComplete="off"
          value={barcode}
          onChange={(e) => setBarcode(e.target.value)}
        />
        {problem && <Alert>{problem}</Alert>}
        {done && (
          <Alert kind="success">
            Lent “{done.bookTitle}” (copy {done.copyBarcode}) to {done.userName}. Due back on {formatDate(done.dueAt)}.
          </Alert>
        )}
        <Button type="submit" loading={issue.isPending} className="w-full">
          Issue book
        </Button>
      </form>
    </Card>
  )
}

function ReturnCard() {
  const refresh = useRefreshAfterDeskAction()
  const barcodeRef = useRef<HTMLInputElement>(null)
  const [barcode, setBarcode] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const [done, setDone] = useState<Loan | null>(null)

  const giveBack = useMutation({
    mutationFn: () => loansApi.returnCopy(barcode.trim()),
    onSuccess: (loan) => {
      setDone(loan)
      setProblem(null)
      setBarcode('')
      refresh()
      barcodeRef.current?.focus()
    },
    onError: (error) => {
      setDone(null)
      setProblem(describeError(error))
      barcodeRef.current?.select()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setDone(null)
    if (!barcode.trim()) return setProblem('Scan or type the barcode of the book.')
    setProblem(null)
    giveBack.mutate()
  }

  return (
    <Card title="Return a book">
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField
          ref={barcodeRef}
          id="return-barcode"
          label="Barcode of the book being returned"
          autoComplete="off"
          value={barcode}
          onChange={(e) => setBarcode(e.target.value)}
        />
        {problem && <Alert>{problem}</Alert>}
        {done && (
          <>
            <Alert kind="success">
              “{done.bookTitle}” (copy {done.copyBarcode}) returned by {done.userName}.
            </Alert>
            {done.fineAmount != null ? (
              <Alert>This book was late. A fine of {formatMoney(done.fineAmount)} has been added to {done.userName}’s account.</Alert>
            ) : (
              <p className="text-sm text-slate-500">Returned on time: no fine.</p>
            )}
          </>
        )}
        <Button type="submit" loading={giveBack.isPending} className="w-full">
          Return book
        </Button>
      </form>
    </Card>
  )
}
