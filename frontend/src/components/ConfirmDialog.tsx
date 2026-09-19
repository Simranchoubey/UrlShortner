import { Modal } from "./Modal";
import { ButtonSpinner } from "./Spinner";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: React.ReactNode;
  confirmLabel?: string;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/** Small destructive-action confirmation dialog (used for URL deletion). */
export function ConfirmDialog({
  open,
  message,
  confirmLabel = "Delete",
  busy = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={confirmLabel}>
      <div className="confirm">
        <div className="confirm__icon" aria-hidden="true">
          !
        </div>
        <div className="confirm__body">{message}</div>
        <div className="confirm__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn btn--danger" onClick={onConfirm} disabled={busy}>
            {busy ? <ButtonSpinner /> : confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  );
}