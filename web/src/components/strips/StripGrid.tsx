import type { StripDTO } from '@shared/types';
import { StripCard } from './StripCard';

export function StripGrid({ strips }: { strips: StripDTO[] }) {
  return (
    <div className="grid grid-cols-1 items-start gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {strips.map((strip) => (
        <StripCard key={strip.id} strip={strip} />
      ))}
    </div>
  );
}
