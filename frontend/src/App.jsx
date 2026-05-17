import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';
import { isAuthenticated, getStoredUser } from './api/client';
import { ThemeProvider } from './context/ThemeContext';
import LandingPage from './pages/LandingPage';
import DashboardPage from './pages/DashboardPage';
import UploadPage from './pages/UploadPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import ProfilePage from './pages/ProfilePage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import AdminPage from './pages/AdminPage';

function PrivateRoute({ element }) {
  return isAuthenticated() ? element : <Navigate to="/login" replace />;
}

function PublicRoute({ element }) {
  return isAuthenticated() ? <Navigate to="/dashboard" replace /> : element;
}

// Stealth mode: non-admin hits "/" → landing (or /dashboard if authenticated).
// Panel varlığını 403 ekranıyla ifşa etmez.
function AdminRoute({ element }) {
  const user = getStoredUser();
  if (!isAuthenticated() || user?.role !== 'ROLE_ADMIN') {
    return <Navigate to="/" replace />;
  }
  return element;
}

function App() {
  return (
    <ThemeProvider>
      <Toaster
        position="top-right"
        richColors
        closeButton
        duration={4000}
        toastOptions={{
          style: { fontFamily: 'inherit' },
        }}
      />
      <BrowserRouter>
        <Routes>
          <Route path="/"              element={isAuthenticated() ? <Navigate to="/dashboard" replace /> : <LandingPage />} />
          <Route path="/login"         element={<PublicRoute element={<LoginPage />} />} />
          <Route path="/register"      element={<PublicRoute element={<RegisterPage />} />} />
          <Route path="/verify-email"    element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<PublicRoute element={<ForgotPasswordPage />} />} />
          <Route path="/reset-password"  element={<ResetPasswordPage />} />
          <Route path="/dashboard" element={<PrivateRoute element={<DashboardPage />} />} />
          <Route path="/upload"    element={<PrivateRoute element={<UploadPage />} />} />
          <Route path="/profile"   element={<PrivateRoute element={<ProfilePage />} />} />
          <Route path="/admin"     element={<AdminRoute   element={<AdminPage />} />} />
          <Route path="*"          element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
