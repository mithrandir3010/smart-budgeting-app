import axios from 'axios';

const client = axios.create({
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor: JWT token'ı her isteğe ekle ──────────────────────────
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor: 401 → login sayfasına yönlendir ────────────────────
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_info');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const register = (data) =>
  client.post('/api/v1/auth/register', data);

export const login = (data) =>
  client.post('/api/v1/auth/login', data);

// ── Analytics (userId artık JWT'den geliyor) ──────────────────────────────────
export const getAnalyticsSummary = () =>
  client.get('/api/v1/analytics/summary');

export const getTransactions = () =>
  client.get('/api/v1/analytics/transactions');

export const getSubscriptions = () =>
  client.get('/api/v1/analytics/subscriptions');

// ── Statement ─────────────────────────────────────────────────────────────────
export const uploadStatement = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return client.post('/api/v1/statements/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// ── Token yardımcıları ────────────────────────────────────────────────────────
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
