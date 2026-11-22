import { lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { HomeRedirect, ProtectedRoute, PublicOnlyRoute, RequireRole } from './auth/RouteGuards'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import NotFoundPage from './pages/NotFoundPage'

// Pages load on demand so the login screen's download stays small.
const CataloguePage = lazy(() => import('./features/catalogue/CataloguePage'))
const BookDetailPage = lazy(() => import('./features/catalogue/BookDetailPage'))
const MyReservationsPage = lazy(() => import('./features/student/MyReservationsPage'))
const MyBorrowingsPage = lazy(() => import('./features/student/MyBorrowingsPage'))
const MyFinesPage = lazy(() => import('./features/student/MyFinesPage'))
const StudentSeatsPage = lazy(() => import('./features/student/StudentSeatsPage'))
const StudentDashboard = lazy(() => import('./features/student/StudentDashboard'))
const LibrarianDashboard = lazy(() => import('./features/librarian/LibrarianDashboard'))
const ManageBooksPage = lazy(() => import('./features/librarian/ManageBooksPage'))
const CopiesPage = lazy(() => import('./features/librarian/CopiesPage'))
const DeskPage = lazy(() => import('./features/librarian/DeskPage'))
const StaffReservationsPage = lazy(() => import('./features/librarian/StaffReservationsPage'))
const StaffFinesPage = lazy(() => import('./features/librarian/StaffFinesPage'))
const StaffSeatsPage = lazy(() => import('./features/librarian/StaffSeatsPage'))
const UsersPage = lazy(() => import('./features/admin/UsersPage'))
const AdminDashboard = lazy(() => import('./features/admin/AdminDashboard'))

export default function App() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomeRedirect />} />
          <Route path="/catalogue" element={<CataloguePage />} />
          <Route path="/catalogue/:id" element={<BookDetailPage />} />
          <Route element={<RequireRole roles={['STUDENT']} />}>
            <Route path="/student" element={<StudentDashboard />} />
            <Route path="/student/reservations" element={<MyReservationsPage />} />
            <Route path="/student/borrowings" element={<MyBorrowingsPage />} />
            <Route path="/student/fines" element={<MyFinesPage />} />
            <Route path="/student/seats" element={<StudentSeatsPage />} />
          </Route>
          <Route element={<RequireRole roles={['LIBRARIAN', 'ADMIN']} />}>
            <Route path="/librarian" element={<LibrarianDashboard />} />
            <Route path="/librarian/books" element={<ManageBooksPage />} />
            <Route path="/librarian/books/:id" element={<CopiesPage />} />
            <Route path="/librarian/desk" element={<DeskPage />} />
            <Route path="/librarian/reservations" element={<StaffReservationsPage />} />
            <Route path="/librarian/fines" element={<StaffFinesPage />} />
            <Route path="/librarian/seats" element={<StaffSeatsPage />} />
          </Route>
          <Route element={<RequireRole roles={['ADMIN']} />}>
            <Route path="/admin" element={<AdminDashboard />} />
            <Route path="/admin/users" element={<UsersPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
