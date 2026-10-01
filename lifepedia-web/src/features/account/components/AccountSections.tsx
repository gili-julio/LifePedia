import { DownloadIcon, FileSpreadsheetIcon, Loader2Icon, Trash2Icon, UploadIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { downloadExport, downloadSpreadsheet } from '../api'
import { DeleteAccountDialog } from './DeleteAccountDialog'
import { ImportDialog } from './ImportDialog'

/** Seções do perfil: exportar/importar dados e excluir a conta. */
export function AccountSections() {
  const [downloading, setDownloading] = useState<'json' | 'xlsx' | null>(null)
  const [importing, setImporting] = useState(false)
  const [deleting, setDeleting] = useState(false)

  async function download(kind: 'json' | 'xlsx') {
    setDownloading(kind)
    try {
      await (kind === 'json' ? downloadExport() : downloadSpreadsheet())
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível baixar.')
    } finally {
      setDownloading(null)
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Meus dados</CardTitle>
          <CardDescription>
            Baixe tudo o que é seu (financeiro e tarefas pessoais, e seus itens nos grupos) ou traga dados de outra conta.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" disabled={!!downloading} onClick={() => download('json')}>
              {downloading === 'json' ? <Loader2Icon className="animate-spin" /> : <DownloadIcon />} Exportar (.json)
            </Button>
            <Button variant="outline" disabled={!!downloading} onClick={() => download('xlsx')}>
              {downloading === 'xlsx' ? <Loader2Icon className="animate-spin" /> : <FileSpreadsheetIcon />} Planilha (.xlsx)
            </Button>
            <Button variant="outline" onClick={() => setImporting(true)}>
              <UploadIcon /> Importar
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            O .json é o que dá para importar depois; a planilha é só para ver. Os arquivos têm seus dados financeiros e suas tarefas:
            guarde em lugar seguro.
          </p>
        </CardContent>
      </Card>

      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle>Excluir conta</CardTitle>
          <CardDescription>
            Apaga seu perfil e seus dados. Nos grupos, seus itens saem, o administrador passa para outro membro e grupo
            só seu é excluído.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={() => setDeleting(true)}>
            <Trash2Icon /> Excluir minha conta
          </Button>
        </CardContent>
      </Card>

      <ImportDialog open={importing} onOpenChange={setImporting} />
      <DeleteAccountDialog open={deleting} onOpenChange={setDeleting} />
    </>
  )
}
