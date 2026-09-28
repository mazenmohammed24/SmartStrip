import { Button } from '../ui/Button';

export function AllOnAllOffButtons({ onAllOn, onAllOff, disabled }: { onAllOn: () => void; onAllOff: () => void; disabled?: boolean }) {
  return (
    <div className="flex gap-2">
      <Button variant="secondary" size="sm" onClick={onAllOn} disabled={disabled}>
        All on
      </Button>
      <Button variant="secondary" size="sm" onClick={onAllOff} disabled={disabled}>
        All off
      </Button>
    </div>
  );
}
