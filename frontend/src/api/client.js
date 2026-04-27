import axios from 'axios';
import { toast } from 'sonner';

const client = axios.create({
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // HttpOnly cookie her istekle otomatik gönderilir
});

// ── Response interceptor ──────────────────────────────────────────────────────
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status  = error.response?.status;
    const message = error.response?.data?.message;

    if (error.code === 'ECONNABORTED' || !error.response) {
      toast.error('Sunucu yanıt vermedi. PDF işleme zaman aşımına uğramış olabilir. Lütfen tekrar deneyin.');
      return Promise.reject(error);
    }

    if (status === 401) {
      clearAuth();
      if (!window.location.pathname.includes('/login')) {
        toast.error('Oturum süreniz doldu, lütfen tekrar giriş yapın.');
        setTimeout(() => { window.location.href = '/login'; }, 1500);
      }
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

// ── Analytics ─────────────────────────────────────────────────────────────────
export const getAnalyticsSummary = () =>
  client.get('/api/v1/analytics/summary');

export const getTransactions = () =>
  client.get('/api/v1/analytics/transactions');

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

export default client;
