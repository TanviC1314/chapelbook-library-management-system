import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usersApi } from '../../api/admin'
import type { Role, User } from '../../api/types'
import { useCurrentUser } from '../../auth/AuthContext'
import { roleLabel } from '../../auth/roles'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { SelectField, TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { CreateUserModal, EditUserModal, ResetPasswordModal } from './UserModals'

const PAGE_SIZE = 15
const ROLES: Role[] = ['STUDENT', 'LIBRARIAN', 'ADMIN']

const roleTone: Record<Role, string> = {
  STUDENT: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  LIBRARIAN: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  ADMIN: 'bg-violet-50 text-violet-700 ring-violet-600/20',
}

type Dialog = { kind: 'create' } | { kind: 'edit'; user: User } | { kind: 'password'; user: User }

export default function UsersPage() {
  const me = useCurrentUser()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const role = ROLES.find((r) => r === params.get('role'))
  const state = params.get('state') // 'active' | 'deactivated' | null (everyone)
  const active = state === 'active' ? true : state === 'deactivated' ? false : undefined
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)

  const [text, setText] = useState(q)
  const debounced = useDebouncedValue(text, 300)
  const [dialog, setDialog] = useState<Dialog | null>(null)

  function update(changes: Record<string, string | undefined>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  useEffect(() => {
    if (debounced.trim() !== q) update({ q: debounced.trim() || undefined })
    // update() is recreated every render; only a change in the debounced text should trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  const users = useQuery({
    queryKey: ['admin-users', { q, role, active, page }],
    queryFn: () => usersApi.list({ q, role, active, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader
        title="Users"
        subtitle="Everyone with an account: students, librarians and administrators."
        actions={<Button onClick={() => setDialog({ kind: 'create' })}>Add an account</Button>}
      />

      <Card className="mb-6">
        <div className="grid gap-4 md:grid-cols-4">
          <div className="md:col-span-2">
            <TextField id="search" label="Search" type="search" placeholder="Name, email or student number" value={text} onChange={(e) => setText(e.target.value)} />
          </div>
          <SelectField id="role" label="Role" value={role ?? ''} onChange={(e) => update({ role: e.target.value || undefined })}>
            <option value="">All roles</option>
            <option value="STUDENT">Students</option>
            <option value="LIBRARIAN">Librarians</option>
            <option value="ADMIN">Administrators</option>
          </SelectField>
          <SelectField id="state" label="Status" value={state ?? ''} onChange={(e) => update({ state: e.target.value || undefined })}>
            <option value="">Active and deactivated</option>
            <option value="active">Active</option>
            <option value="deactivated">Deactivated</option>
          </SelectField>
        </div>
      </Card>

      <Card className="p-0 [&>div]:p-0">
        {users.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading users">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : users.isError ? (
          <ErrorState error={users.error} onRetry={() => users.refetch()} />
        ) : users.data.content.length === 0 ? (
          <EmptyState title="No accounts found" message="Try a different search or filter." />
        ) : (
          <>
            <div className={`overflow-x-auto ${users.isPlaceholderData ? 'opacity-60' : ''}`}>
              <table className="w-full min-w-[44rem] text-left text-sm">
                <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-medium">Name</th>
                    <th scope="col" className="px-3 py-3 font-medium">Role</th>
                    <th scope="col" className="px-3 py-3 font-medium">Status</th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.data.content.map((user) => (
                    <tr key={user.id}>
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">
                          {user.fullName}
                          {user.id === me.id && <span className="ml-2 text-xs font-normal text-slate-400">(you)</span>}
                        </p>
                        <p className="text-xs text-slate-500">
                          {user.email}
                          {user.studentNumber ? ` · ${user.studentNumber}` : ''}
                        </p>
                      </td>
                      <td className="px-3 py-3">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${roleTone[user.role]}`}>{roleLabel[user.role]}</span>
                      </td>
                      <td className="px-3 py-3">
                        <span className={user.active ? 'text-emerald-700' : 'font-medium text-red-700'}>{user.active ? 'Active' : 'Deactivated'}</span>
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Button variant="secondary" onClick={() => setDialog({ kind: 'edit', user })} aria-label={`Edit ${user.fullName}`}>
                            Edit
                          </Button>
                          <Button variant="ghost" onClick={() => setDialog({ kind: 'password', user })} aria-label={`Reset password for ${user.fullName}`}>
                            Reset password
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={users.data.page}
              totalPages={users.data.totalPages}
              totalElements={users.data.totalElements}
              noun="account"
              onPageChange={(next) => update({ page: next === 0 ? undefined : String(next) }, false)}
            />
          </>
        )}
      </Card>

      {dialog?.kind === 'create' && <CreateUserModal onClose={() => setDialog(null)} />}
      {dialog?.kind === 'edit' && <EditUserModal user={dialog.user} isSelf={dialog.user.id === me.id} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'password' && <ResetPasswordModal user={dialog.user} onClose={() => setDialog(null)} />}
    </>
  )
}
