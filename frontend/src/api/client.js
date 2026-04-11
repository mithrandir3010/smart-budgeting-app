import axios from 'axios';

const client = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const getAnalyticsSummary = (userId) =>
  client.get('/api/v1/analytics/summary', { params: { userId } });

export const uploadStatement = (file, userId) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('userId', userId);
  return client.post('/api/v1/statements/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export default client;
