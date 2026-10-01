import { Loader2Icon } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { useJoinByLink, useLinkPreview } from '../api'
import { GroupAvatar } from '../components/GroupAvatar'

/** Entrar num grupo pelo link de convite. */
export function JoinGroupPage() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const { data, isLoading } = useLinkPreview(token)
  const join = useJoinByLink()

  async function enter() {
    try {
      const group = await join.mutateAsync(token)
      toast.success(`Você entrou em ${group.name}.`)
      navigate(`/grupos/${group.id}`, { replace: true })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível entrar no grupo.')
    }
  }

  return (
    <div className="mx-auto max-w-sm pt-6">
      {isLoading ? (
        <Skeleton className="h-64" />
      ) : !data ? (
        <Card>
          <CardHeader>
            <CardTitle>Convite inválido</CardTitle>
            <CardDescription>O link expirou ou foi desativado. Peça um novo para quem administra o grupo.</CardDescription>
          </CardHeader>
          <CardContent><Button variant="outline" asChild><Link to="/grupos">Ir para grupos</Link></Button></CardContent>
        </Card>
      ) : (
        <Card className="text-center">
          <CardHeader className="items-center justify-items-center gap-3">
            <GroupAvatar name={data.name} imageUrl={data.imageUrl} size="xl" />
            <CardTitle className="text-xl">{data.name}</CardTitle>
            <CardDescription>{data.memberCount} participante{data.memberCount === 1 ? '' : 's'}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {data.member ? (
              <Button className="w-full" asChild><Link to={`/grupos/${data.groupId}`}>Você já participa · abrir grupo</Link></Button>
            ) : (
              <>
                <p className="text-sm text-muted-foreground">
                  Ao entrar, os membros veem os itens que você levar para o grupo. Seus outros dados continuam só seus.
                </p>
                <Button className="w-full" onClick={enter} disabled={join.isPending}>
                  {join.isPending && <Loader2Icon className="animate-spin" />} Entrar no grupo
                </Button>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
