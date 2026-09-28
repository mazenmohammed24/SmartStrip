import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <p className="text-4xl">🔌</p>
      <p className="text-lg font-medium text-text">Page not found</p>
      <Link to="/" className="text-sm text-primary hover:underline">
        Back to dashboard
      </Link>
    </div>
  );
}
