import { NavLink } from 'react-router-dom';
import { useUiStore } from '../../store/uiStore';
import { Sheet } from '../ui/Sheet';
import { SECONDARY_NAV } from './navConfig';

export function MoreSheet() {
  const open = useUiStore((s) => s.moreSheetOpen);
  const setOpen = useUiStore((s) => s.setMoreSheetOpen);

  return (
    <Sheet open={open} onClose={() => setOpen(false)} title="More">
      <div className="flex flex-col gap-1">
        {SECONDARY_NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={() => setOpen(false)}
            className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-text hover:bg-surface-hover"
          >
            <item.Icon size={18} strokeWidth={1.8} />
            {item.label}
          </NavLink>
        ))}
      </div>
    </Sheet>
  );
}
