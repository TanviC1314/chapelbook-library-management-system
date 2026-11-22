import { useCurrentUser } from '../../auth/AuthContext'
import { roleLabel } from '../../auth/roles'
import { PageHeader } from '../../components/ui/PageHeader'

/** The landing page of each portal. It is replaced by a real dashboard for each role as those are built. */
export default function PortalHome() {
  const user = useCurrentUser()
  return <PageHeader title={`Welcome, ${user.fullName}`} subtitle={`${roleLabel[user.role]} portal`} />
}
