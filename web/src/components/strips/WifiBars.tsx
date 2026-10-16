import { rssiLevel } from '../../lib/formatters';

export function WifiBars({ rssi, size = 16 }: { rssi: number | null | undefined; size?: number }) {
  const level = rssiLevel(rssi);
  const label = rssi === null || rssi === undefined ? 'Wi-Fi signal unknown' : ['No signal', 'Weak Wi-Fi', 'Fair Wi-Fi', 'Good Wi-Fi', 'Strong Wi-Fi'][level];
  return (
    <span role="img" aria-label={label} title={label} className="inline-flex items-end gap-px" style={{ height: size, width: size }}>
      {[1, 2, 3, 4].map((bar) => (
        <span
          key={bar}
          className={`w-1 flex-1 rounded-[1px] ${bar <= level ? 'bg-primary' : 'bg-border'}`}
          style={{ height: `${bar * 25}%` }}
        />
      ))}
    </span>
  );
}
