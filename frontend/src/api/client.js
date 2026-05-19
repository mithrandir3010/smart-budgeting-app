import axios from 'axios';
import { toast } from 'sonner';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // HttpOnly cookie her istekle otomatik gönderilir
});

// ── Token yenileme durumu — eş zamanlı 401'leri tek refresh'e indirger ────────
let isRefreshing = false;
let pendingQueue = [];

function drainQueue(error) {
  pendingQueue.forEach(({ resolve, reject }) => (error ? reject(error) : resolve()));
  pendingQueue = [];
}

function redirectToLogin() {
  clearAuth();
  if (!window.location.pathname.includes('/login')) {
    toast.error('Oturum süreniz doldu, lütfen tekrar giriş yapın.');
    setTimeout(() => { window.location.href = '/login'; }, 1500);
  }
}

// ── Response interceptor ──────────────────────────────────────────────────────
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status  = error.response?.status;
    const message = error.response?.data?.message;
    const config  = error.config;

    if (error.code === 'ECONNABORTED' || !error.response) {
      toast.error('Sunucuya ulasilamadi veya CORS engeline takildi. Backend URL ve CORS ayarlarini kontrol edin.');
      return Promise.reject(error);
    }

    // Auth endpoint'leri (login/register/refresh/logout) için refresh döngüsüne girme
    if (status === 401 && !config._retry && !config.url?.includes('/auth/')) {
      config._retry = true;

      if (isRefreshing) {
        // Refresh devam ediyor — bu isteği tamamlanınca yeniden dene
        return new Promise((resolve, reject) =>
          pendingQueue.push({ resolve, reject })
        ).then(() => client(config)).catch(() => Promise.reject(error));
      }

      isRefreshing = true;
      try {
        await client.post('/api/v1/auth/refresh');
        drainQueue(null);
        return client(config);
      } catch {
        drainQueue(new Error('session_expired'));
        redirectToLogin();
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    // Auth endpoint'lerinden gelen 401 veya refresh başarısız → login'e yönlendir
    if (status === 401 && !config.url?.includes('/auth/')) {
      redirectToLogin();
    } else if (status === 409) {
      toast.warning(message || 'Bu işlem zaten mevcut.');
    } else if (status === 413) {
      toast.error('Dosya boyutu çok büyük (max 2MB).');
    } else if (status >= 500) {
      toast.error('PDF analizi başarısız oldu. Lütfen tekrar deneyin.');
    }

    return Promise.reject(error);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const register = (data) =>
  client.post('/api/v1/auth/register', data);

export const login = (data) =>
  client.post('/api/v1/auth/login', data);

export const logoutApi = () =>
  client.post('/api/v1/auth/logout');

export const verifyEmail = (token) =>
  client.get('/api/v1/auth/verify-email', { params: { token } });

export const forgotPassword = (email) =>
  client.post('/api/v1/auth/forgot-password', { email });

export const resetPassword = (token, newPassword, confirmPassword) =>
  client.post('/api/v1/auth/reset-password', { token, newPassword, confirmPassword });

// ── Analytics ─────────────────────────────────────────────────────────────────
export const getAnalyticsSummary = (statementId = null) =>
  client.get('/api/v1/analytics/summary', { params: statementId ? { statementId } : {} });

export const getTransactions = () =>
  client.get('/api/v1/analytics/transactions');

export const getStatements = () =>
  client.get('/api/v1/statements');

export const getSubscriptions = () =>
  client.get('/api/v1/analytics/subscriptions');

// ── Budget Limits ─────────────────────────────────────────────────────────────
export const getBudgetLimits = () =>
  client.get('/api/v1/budget-limits');

export const upsertBudgetLimit = (data) =>
  client.post('/api/v1/budget-limits', data);

export const deleteBudgetLimit = (id) =>
  client.delete(`/api/v1/budget-limits/${id}`);

export const getBudgetAlerts = () =>
  client.get('/api/v1/budget-limits/alerts');

// ── User Profile ──────────────────────────────────────────────────────────────
export const getUserProfile = () =>
  client.get('/api/v1/user/profile');

export const updateProfile = (data) =>
  client.put('/api/v1/user/profile', data);

export const changePassword = (data) =>
  client.put('/api/v1/user/change-password', data);

export const updateMonthlyBudget = (amount) =>
  client.put('/api/v1/user/monthly-budget', { monthlyBudget: amount });

// ── Statement ─────────────────────────────────────────────────────────────────
export const deleteAllStatements = () =>
  client.delete('/api/v1/statements/all');

export const uploadStatement = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return client.post('/api/v1/statements/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 210_000,
  });
};

// ── Auth helpers — token localStorage'dan kaldırıldı, HttpOnly cookie'de ─────
export const saveAuth = (userInfo) => {
  localStorage.setItem('user_info', JSON.stringify(userInfo));
};

export const clearAuth = () => {
  localStorage.removeItem('user_info');
};

export const getStoredUser = () => {
  const raw = localStorage.getItem('user_info');
  return raw ? JSON.parse(raw) : null;
};

export const isAuthenticated = () => !!localStorage.getItem('user_info');

// ── Admin ─────────────────────────────────────────────────────────────────────
export const getAdminStats = () =>
  client.get('/api/v1/admin/stats');

export const getAdminUsers = (page = 0, size = 20, search = '') =>
  client.get('/api/v1/admin/users', {
    params: { page, size, ...(search ? { search } : {}) },
  });

export const getAdminUserStatements = (id) =>
  client.get(`/api/v1/admin/users/${id}/statements`);

export const toggleAdminUserStatus = (id, active) =>
  client.put(`/api/v1/admin/users/${id}/status`, { active });

export const getAdminAudit = (page = 0, size = 50) =>
  client.get('/api/v1/admin/audit', { params: { page, size } });

export const getAdminGrowth = () =>
  client.get('/api/v1/admin/growth');

export const getAdminBankStats = () =>
  client.get('/api/v1/admin/bank-stats');

export const getAdminFunnel = () =>
  client.get('/api/v1/admin/funnel');

export const getAdminSilentFailures = () =>
  client.get('/api/v1/admin/silent-failures');

export const getAdminFailedStatements = () =>
  client.get('/api/v1/admin/failed-statements');

export const getAdminSettings = () =>
  client.get('/api/v1/admin/settings');

export const updateAdminSettings = (settings) =>
  client.put('/api/v1/admin/settings', settings);

export const bulkToggleUserStatus = (userIds, active) =>
  client.post('/api/v1/admin/users/bulk-status', { userIds, active });

export const getPublicSettings = () =>
  client.get('/api/v1/settings/public');

export default client;
