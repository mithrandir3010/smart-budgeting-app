import { Component } from 'react';
import { RefreshCw } from 'lucide-react';

/**
 * ErrorBoundary — Bir child bileşen render sırasında fırlatırsa
 * graceful fallback UI gösterir; uygulamanın geri kalanı çalışmaya devam eder.
 *
 * Kullanım:
 *   <ErrorBoundary label="Graf">
 *     <PieChart ... />
 *   </ErrorBoundary>
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, message: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, message: error?.message ?? 'Bilinmeyen hata' };
  }

  componentDidCatch(error, info) {
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  handleRetry = () => {
    this.setState({ hasError: false, message: null });
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    const { label = 'Bileşen' } = this.props;

    return (
      <div className="flex flex-col items-center justify-center gap-3 py-10 px-6 text-center">
        <div className="w-10 h-10 bg-rose-100 dark:bg-rose-900/40 rounded-xl flex items-center justify-center">
          <span className="text-rose-500 text-lg">!</span>
        </div>
        <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
          {label} yüklenemedi
        </p>
        <p className="text-xs text-zinc-400 dark:text-zinc-500 max-w-xs leading-relaxed">
          {this.state.message}
        </p>
        <button
          onClick={this.handleRetry}
          className="flex items-center gap-1.5 text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition-colors"
        >
          <RefreshCw size={13} strokeWidth={2.5} />
          Tekrar Dene
        </button>
      </div>
    );
  }
}
