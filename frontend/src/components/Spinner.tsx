export function Spinner({ label }: { label?: string }) {
  return (
    <span className="spinner-wrap" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      {label ? <span className="spinner-label">{label}</span> : null}
    </span>
  );
}

/** Button-sized spinner used inside primary buttons while submitting. */
export function ButtonSpinner() {
  return <span className="spinner spinner--sm" aria-hidden="true" />;
}