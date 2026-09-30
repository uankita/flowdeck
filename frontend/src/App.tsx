import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import BoardsListPage from './pages/BoardsListPage'
import BoardPage from './pages/BoardPage'
import NotFoundPage from './pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/boards" replace />} />
        <Route path="/boards" element={<BoardsListPage />} />
        <Route path="/boards/:boardKey" element={<BoardPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
