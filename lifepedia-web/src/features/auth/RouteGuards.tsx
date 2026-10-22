import { Loader2Icon } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { returnTo, useAuth } from './auth-context'

function FullScreenLoader() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Loader2Icon className="size-6 animate-spin text-muted-foreground" />
    </div>
  )
}

/** Exige login; senão vai para /login. */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <FullScreenLoader />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

/** Login/cadastro: quem está logado vai para o app (ou para a página que pediu login). */
export function GuestOnly() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <FullScreenLoader />
  if (user) return <Navigate to={returnTo(location.state)} replace />
  return <Outlet />
}
