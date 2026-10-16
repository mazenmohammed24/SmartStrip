import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { ChevronLeft, Plus, Trash2 } from 'lucide-react';
import type { RuleDTO, RuleType } from '@shared/types';
import { RuleEditor } from '../components/rules/RuleEditor';
import { KIND_BY_TYPE, summarize } from '../components/rules/ruleMeta';
import { Badge } from '../components/ui/Badge';
import { Button, Spinner } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Modal } from '../components/ui/Modal';
import { Toggle } from '../components/ui/Toggle';
import { formatRelativeTime } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';
import { useRulesStore } from '../store/rulesStore';

const STATUS_TONE = { idle: 'neutral', waiting: 'info', active: 'success', tripped: 'danger' } as const;

export default function RulesPage() {
  const { stripId, kind } = useParams<{ stripId: string; kind: string }>();
  const strips = useFleetStore((s) => s.strips);
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const rules = useRulesStore((s) => s.rules);
  const rulesLoaded = useRulesStore((s) => s.loaded);
  const fetchRules = useRulesStore((s) => s.fetchRules);
  const setEnabled = useRulesStore((s) => s.setEnabled);
  const removeRule = useRulesStore((s) => s.removeRule);
  const [editing, setEditing] = useState<{ rule: RuleDTO | null } | null>(null);
  const [deleting, setDeleting] = useState<RuleDTO | null>(null);

  useEffect(() => {
    if (!loaded) void fetchStrips();
    void fetchRules();
  }, [loaded, fetchStrips, fetchRules]);

  const meta = kind ? KIND_BY_TYPE[kind as RuleType] : undefined;
  if (!meta) return <Navigate to={`/strips/${stripId}/settings`} replace />;

  const strip = strips.find((s) => s.id === stripId);
  if (!strip) {
    return <div className="py-16 text-center text-text-muted">{loaded ? 'Strip not found.' : <Spinner size={24} />}</div>;
  }

  const list = rules.filter((r) => r.stripId === strip.id && r.type === meta.type);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Link to={`/strips/${strip.id}/settings`} aria-label="Back" className="rounded-lg p-1.5 text-text-muted hover:bg-surface-hover">
          <ChevronLeft size={20} />
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-semibold text-text">{meta.title}</h1>
          <p className="truncate text-xs text-text-muted">{strip.name}</p>
        </div>
        <Button size="sm" icon={<Plus size={16} />} onClick={() => setEditing({ rule: null })}>
          Add
        </Button>
      </div>
      <p className="px-1 text-sm text-text-muted">{meta.blurb}.</p>

      {!rulesLoaded && (
        <div className="flex justify-center py-10">
          <Spinner size={22} />
        </div>
      )}

      {rulesLoaded && list.length === 0 && (
        <EmptyState
          icon={<meta.Icon size={28} />}
          title={`No ${meta.title.toLowerCase()} yet`}
          description={meta.blurb}
          action={<Button onClick={() => setEditing({ rule: null })}>{meta.addLabel}</Button>}
        />
      )}

      <div className="flex flex-col gap-3">
        {list.map((rule) => (
          <Card key={rule.id}>
            <CardBody className="flex items-center gap-3">
              <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setEditing({ rule })}>
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-semibold text-text">{rule.name || meta.title}</p>
                  {rule.enabled && rule.status !== 'idle' && <Badge tone={STATUS_TONE[rule.status]}>{rule.status}</Badge>}
                </div>
                <p className="mt-0.5 text-xs text-text-muted">{summarize(rule, strip)}</p>
                {rule.lastTriggeredAt && <p className="mt-0.5 text-xs text-text-muted">Last triggered {formatRelativeTime(rule.lastTriggeredAt)}</p>}
              </button>
              <button type="button" aria-label="Delete" onClick={() => setDeleting(rule)} className="rounded-lg p-2 text-text-muted hover:bg-danger-bg hover:text-danger">
                <Trash2 size={16} />
              </button>
              <Toggle checked={rule.enabled} onChange={(v) => void setEnabled(rule.id, v)} label={`Enable ${rule.name || meta.title}`} />
            </CardBody>
          </Card>
        ))}
      </div>

      {editing && <RuleEditor key={editing.rule?.id ?? 'new'} open type={meta.type} strip={strip} rule={editing.rule} onClose={() => setEditing(null)} />}

      <Modal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title="Delete this rule?"
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                if (deleting) await removeRule(deleting.id);
                setDeleting(null);
              }}
            >
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-text-muted">{deleting ? summarize(deleting, strip) : ''}</p>
      </Modal>
    </div>
  );
}
