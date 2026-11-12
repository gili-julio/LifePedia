import { MapPinIcon } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { AddressList } from './AddressList'

/** Endereços de entrega também em Meu perfil (os mesmos da página de Entregas). */
export function AddressesProfileCard() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><MapPinIcon className="size-4" aria-hidden /> Endereços de entrega</CardTitle>
        <CardDescription>Usados nas suas entregas. Só você vê o endereço completo.</CardDescription>
      </CardHeader>
      <CardContent><AddressList /></CardContent>
    </Card>
  )
}
