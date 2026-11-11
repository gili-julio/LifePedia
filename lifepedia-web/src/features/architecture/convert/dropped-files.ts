/** Arquivos soltos na área de envio, entrando nas pastas (a exportação do SketchUp põe as texturas numa pasta). */
export async function filesFromDrop(data: DataTransfer): Promise<File[]> {
  const entries = [...data.items]
    .map((item) => (item.kind === 'file' ? item.webkitGetAsEntry?.() : null))
    .filter((entry): entry is FileSystemEntry => !!entry)
  if (entries.length === 0) return [...data.files]
  const files: File[] = []
  for (const entry of entries) await collect(entry, files)
  return files
}

async function collect(entry: FileSystemEntry, out: File[]): Promise<void> {
  if (entry.isFile) {
    out.push(await new Promise<File>((resolve, reject) => (entry as FileSystemFileEntry).file(resolve, reject)))
    return
  }
  if (!entry.isDirectory) return
  const reader = (entry as FileSystemDirectoryEntry).createReader()
  // readEntries devolve em lotes: lê até vir vazio.
  for (;;) {
    const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => reader.readEntries(resolve, reject))
    if (batch.length === 0) return
    for (const child of batch) await collect(child, out)
  }
}
