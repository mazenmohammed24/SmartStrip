import { Modal } from '../ui/Modal';
import { ScanPanel } from '../../pages/ScanPage';

export function AddStripModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} size="lg" title="Add strip">
      <ScanPanel onAdopted={onClose} />
    </Modal>
  );
}
