import { Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { AuthBootstrap } from './app/AuthBootstrap'
import NotFoundPage from './app/NotFoundPage'
import { ProtectedRoute } from './app/ProtectedRoute'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import BoardDetailPage from './features/boards/BoardDetailPage'
import BoardListPage from './features/boards/BoardListPage'
import HomePage from './features/workspaces/HomePage'

export default function App() {
  return (
    <AuthBootstrap>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/w/:workspaceId/boards" element={<BoardListPage />} />
            <Route path="/w/:workspaceId/boards/:boardKey" element={<BoardDetailPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthBootstrap>
  )
}
