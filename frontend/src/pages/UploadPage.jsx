import { useState } from 'react';
import { Link } from 'react-router-dom';
import { uploadStatement } from '../api/client';

const USER_ID = 1;

export default function UploadPage() {
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState(null); // { type: 'success' | 'error', message: string }
  const [loading, setLoading] = useState(false);

  const handleFileChange = (e) => {
    const selected = e.target.files[0];
    if (selected && selected.type !== 'application/pdf') {
      setStatus({ type: 'error', message: 'Lütfen yalnızca PDF dosyası seçin.' });
      setFile(null);
      return;
    }
    setFile(selected || null);
    setStatus(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      setStatus({ type: 'error', message: 'Lütfen bir PDF dosyası seçin.' });
      return;
    }

    setLoading(true);
    setStatus(null);

    try {
      const res = await uploadStatement(file, USER_ID);
      setStatus({ type: 'success', message: res.data });
      setFile(null);
      e.target.reset();
    } catch (err) {
      const msg =
        err.response?.data || 'Yükleme sırasında bir hata oluştu.';
      setStatus({ type: 'error', message: msg });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.container}>
        <div style={styles.topBar}>
          <Link to="/" style={styles.backLink}>
            ← Dashboard'a Dön
          </Link>
        </div>

        <h1 style={styles.title}>Ekstre Yükle</h1>
        <p style={styles.subtitle}>
          Banka ekstrenizi PDF formatında yükleyin. Harcamalar otomatik olarak
          analiz edilecektir.
        </p>

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.dropZone}>
            <input
              type="file"
              accept="application/pdf"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />
            <div style={styles.dropIcon}>📄</div>
            {file ? (
              <>
                <p style={styles.fileName}>{file.name}</p>
                <p style={styles.fileSize}>
                  {(file.size / 1024).toFixed(1)} KB
                </p>
              </>
            ) : (
              <>
                <p style={styles.dropText}>PDF dosyasını seçin</p>
                <p style={styles.dropHint}>veya buraya sürükleyip bırakın</p>
              </>
            )}
          </label>

          {status && (
            <div
              style={
                status.type === 'success'
                  ? styles.alertSuccess
                  : styles.alertError
              }
            >
              {status.message}
            </div>
          )}

          <button
            type="submit"
            disabled={loading || !file}
            style={{
              ...styles.submitBtn,
              opacity: loading || !file ? 0.6 : 1,
              cursor: loading || !file ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? 'Yükleniyor...' : 'Ekstreyi Yükle'}
          </button>
        </form>
      </div>
    </div>
  );
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f9fafb',
    fontFamily: 'system-ui, sans-serif',
    display: 'flex',
    justifyContent: 'center',
    padding: '40px 16px',
  },
  container: {
    width: '100%',
    maxWidth: '520px',
  },
  topBar: {
    marginBottom: '24px',
  },
  backLink: {
    color: '#6366f1',
    textDecoration: 'none',
    fontSize: '14px',
    fontWeight: '500',
  },
  title: {
    margin: '0 0 8px',
    fontSize: '28px',
    fontWeight: '700',
    color: '#111827',
  },
  subtitle: {
    margin: '0 0 28px',
    fontSize: '15px',
    color: '#6b7280',
    lineHeight: '1.5',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  dropZone: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    border: '2px dashed #d1d5db',
    borderRadius: '12px',
    padding: '48px 24px',
    background: '#fff',
    cursor: 'pointer',
    transition: 'border-color 0.2s',
    textAlign: 'center',
  },
  dropIcon: {
    fontSize: '48px',
    marginBottom: '12px',
  },
  dropText: {
    margin: '0 0 4px',
    fontSize: '16px',
    fontWeight: '600',
    color: '#374151',
  },
  dropHint: {
    margin: 0,
    fontSize: '13px',
    color: '#9ca3af',
  },
  fileName: {
    margin: '0 0 4px',
    fontSize: '15px',
    fontWeight: '600',
    color: '#111827',
    wordBreak: 'break-all',
  },
  fileSize: {
    margin: 0,
    fontSize: '13px',
    color: '#6b7280',
  },
  alertSuccess: {
    background: '#d1fae5',
    color: '#065f46',
    border: '1px solid #6ee7b7',
    borderRadius: '8px',
    padding: '12px 16px',
    fontSize: '14px',
  },
  alertError: {
    background: '#fee2e2',
    color: '#991b1b',
    border: '1px solid #fca5a5',
    borderRadius: '8px',
    padding: '12px 16px',
    fontSize: '14px',
  },
  submitBtn: {
    background: '#6366f1',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    padding: '14px',
    fontSize: '16px',
    fontWeight: '600',
    transition: 'opacity 0.2s',
  },
};
