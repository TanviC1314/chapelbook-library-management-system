import { Route, Routes } from 'react-router-dom'
import { HomeRedirect, ProtectedRoute, PublicOnlyRoute, RequireRole } from './auth/RouteGuards'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import PortalHome from './features/home/PortalHome'
import NotFoundPage from './pages/NotFoundPage'

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
          <Route element={<RequireRole roles={['STUDENT']} />}>
            <Route path="/student" element={<PortalHome />} />
          </Route>
          <Route element={<RequireRole roles={['LIBRARIAN', 'ADMIN']} />}>
            <Route path="/librarian" element={<PortalHome />} />
          </Route>
          <Route element={<RequireRole roles={['ADMIN']} />}>
            <Route path="/admin" element={<PortalHome />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
