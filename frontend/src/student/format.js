// Money arrives as integer paise; never do arithmetic on it in floating point, only format it for display.
export function money(paise, currency = 'INR') {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: paise % 100 === 0 ? 0 : 2 })
    .format(paise / 100);
}

export const dateTime = (v) => (v ? new Date(v).toLocaleString() : '—');
export const dateOnly = (v) => (v ? new Date(v).toLocaleDateString() : '—');

export const STATUS_CHIP = { PAID: 'green', REFUNDED: 'amber', CREATED: 'amber', FAILED: 'red', PROCESSED: 'green',
  REQUESTED: 'amber', REJECTED: 'red', ACTIVE: 'green', REPLACED: 'amber', DEACTIVATED: 'red' };
