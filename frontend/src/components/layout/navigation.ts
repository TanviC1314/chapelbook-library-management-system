import {
  Armchair,
  BarChart3,
  BookMarked,
  BookOpen,
  ClipboardList,
  LayoutDashboard,
  Library,
  Receipt,
  ScanBarcode,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '../../api/types'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** Match this exact path only, so a portal's home link is not highlighted on every page below it. */
  end?: boolean
}

const catalogue: NavItem = { to: '/catalogue', label: 'Catalogue', icon: Library }

const studentNav: NavItem[] = [
  { to: '/student', label: 'Dashboard', icon: LayoutDashboard, end: true },
  catalogue,
  { to: '/student/borrowings', label: 'My borrowings', icon: BookOpen },
  { to: '/student/reservations', label: 'My reservations', icon: BookMarked },
  { to: '/student/fines', label: 'Fines', icon: Receipt },
  { to: '/student/seats', label: 'Study seats', icon: Armchair },
]

const staffDesk: NavItem[] = [
  catalogue,
  { to: '/librarian/books', label: 'Manage books', icon: BookOpen },
  { to: '/librarian/desk', label: 'Issue and return', icon: ScanBarcode },
  { to: '/librarian/reservations', label: 'Reservations', icon: BookMarked },
  { to: '/librarian/fines', label: 'Fines', icon: Receipt },
  { to: '/librarian/seats', label: 'Seats', icon: Armchair },
]

const librarianNav: NavItem[] = [{ to: '/librarian', label: 'Dashboard', icon: LayoutDashboard, end: true }, ...staffDesk]

const adminNav: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/reports', label: 'Reports', icon: BarChart3 },
  { to: '/librarian', label: 'Library desk', icon: ClipboardList, end: true },
  ...staffDesk,
]

/** What each kind of account sees in the navigation. */
export function navFor(role: Role): NavItem[] {
  switch (role) {
    case 'STUDENT':
      return studentNav
    case 'LIBRARIAN':
      return librarianNav
    case 'ADMIN':
      return adminNav
  }
}
