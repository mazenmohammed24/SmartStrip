import { useEffect, useState } from 'react';

export type Breakpoint = 'mobile' | 'tablet' | 'desktop';

function computeBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return 'desktop';
  const w = window.innerWidth;
  if (w < 640) return 'mobile';
  if (w < 1024) return 'tablet';
  return 'desktop';
}

export function useBreakpoint(): Breakpoint {
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(computeBreakpoint);

  useEffect(() => {
    const update = () => setBreakpoint(computeBreakpoint());
    const mqTablet = window.matchMedia('(min-width: 640px)');
    const mqDesktop = window.matchMedia('(min-width: 1024px)');
    mqTablet.addEventListener('change', update);
    mqDesktop.addEventListener('change', update);
    window.addEventListener('resize', update);
    return () => {
      mqTablet.removeEventListener('change', update);
      mqDesktop.removeEventListener('change', update);
      window.removeEventListener('resize', update);
    };
  }, []);

  return breakpoint;
}
