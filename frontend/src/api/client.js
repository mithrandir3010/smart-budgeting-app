import axios from 'axios';
import { toast } from 'sonner';

const client = axios.create({
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor: JWT token'ı her isteğe ekle ─────────────────────────
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response interceptor: hata kodlarını yakalayıp toast göster ──────────────
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status  = error.response?.status;
    const message = error.response?.data?.message;

    if (status === 401) {
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_info');
      // Login sayfasındayken sonsuz döngü olmasın
      if (!window.location.pathname.includes('/login')) {
        toast.error('Oturum süreniz doldu, lütfen tekrar giriş yapın.');
        setTimeout(() => { window.location.href = '/login'; }, 1500);
      }
    } else if (status === 409) {
      toast.warning(message || 'Bu işlem zaten mevcut.');
    } else if (status === 413) {
      toast.error('Dosya boyutu çok büyük (max 2MB).');
    } else if (status >= 500) {
      toast.error('Sunucu hatası. Lütfen daha sonra tekrar deneyin.');
    }
    // 400/422/404 hataları bileşen seviyesinde yönetilir (forma özel mesajlar)

    return Promise.reject(error);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const register = (data) =>
  client.post('/api/v1/auth/register', data);

export const login = (data) =>
  client.post('/api/v1/auth/login', data);

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
  });
};

// ── Auth helpers ──────────────────────────────────────────────────────────────
export const saveAuth = (token, userInfo) => {
  localStorage.setItem('jwt_token', token);
  localStorage.setItem('user_info', JSON.stringify(userInfo));
};

export const clearAuth = () => {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_info');
};

export const getStoredUser = () => {
  const raw = localStorage.getItem('user_info');
  return raw ? JSON.parse(raw) : null;
};

export const isAuthenticated = () => !!localStorage.getItem('jwt_token');

export default client;
