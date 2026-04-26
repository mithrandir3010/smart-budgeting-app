import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

export function fmt(n, currency = 'TRY') {
  return Number(n).toLocaleString('tr-TR', { style: 'currency', currency });
}
