import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Input, Label } from '../components/ui/Input';
import { useAuthStore } from '../store/authStore';

export default function SetupPage() {
  const setup = useAuthStore((s) => s.setup);
  const storeError = useAuthStore((s) => s.error);
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (password !== confirm) {
      setLocalError('Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      await setup(username, password);
      navigate('/', { replace: true });
    } catch {
      // surfaced via storeError
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="mb-6 text-center">
          <div className="mb-2 text-4xl">🔌</div>
          <h1 className="text-xl font-semibold text-text">Set up SmartStrip</h1>
          <p className="mt-1 text-sm text-text-muted">Create the admin account for this portal. This runs once.</p>
        </div>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <div>
            <Label htmlFor="username">Username</Label>
            <Input id="username" value={username} onChange={(e) => setUsername(e.target.value)} required minLength={3} autoFocus />
          </div>
          <div>
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
          </div>
          <div>
            <Label htmlFor="confirm">Confirm password</Label>
            <Input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required minLength={8} />
          </div>
          {(localError ?? storeError) && <p className="text-sm text-danger">{localError ?? storeError}</p>}
          <Button type="submit" loading={loading} className="w-full">
            Create account
          </Button>
        </form>
      </div>
    </div>
  );
}
