import type { ConnectivityState } from '@shared/types';
import { Badge } from '../ui/Badge';

export function ConnectivityBadge({ connectivity }: { connectivity: ConnectivityState }) {
  if (connectivity === 'online') return <Badge tone="success">● Online</Badge>;
  if (connectivity === 'offline') return <Badge tone="danger">● Offline</Badge>;
  return <Badge tone="warning">● Unreachable</Badge>;
}
