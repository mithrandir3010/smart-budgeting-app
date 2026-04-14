import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { isAuthenticated } from './api/client';
import DashboardPage from './pages/DashboardPage';
import UploadPage from './pages/UploadPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

/** JWT varsa korunan sayfayı göster, yoksa /login'e yönlendir. */
function PrivateRoute({ element }) {
  return isAuthenticated() ? element : <Navigate to="/login" replace />;
}

/** Zaten giriş yapılmışsa / 'ya yönlendir. */
function PublicRoute({ element }) {
  return isAuthenticated() ? <Navigate to="/" replace /> : element;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login"    element={<PublicRoute element={<LoginPage />} />} />
        <Route path="/register" element={<PublicRoute element={<RegisterPage />} />} />
        <Route path="/"         element={<PrivateRoute element={<DashboardPage />} />} />
        <Route path="/upload"   element={<PrivateRoute element={<UploadPage />} />} />
        <Route path="*"         element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
