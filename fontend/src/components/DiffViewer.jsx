import { useState } from 'react'
import { ChevronDown, FileCode2, PanelRightClose } from 'lucide-react'

function DiffViewer({ files }) {
  const [openFiles, setOpenFiles] = useState(() => Object.fromEntries((files || []).map((file) => [file.fileName, true])))

  const toggleFile = (fileName) => {
    setOpenFiles((current) => ({ ...current, [fileName]: !current[fileName] }))
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-[20px] border border-slate-200/70 bg-white/80 px-4 py-3 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
          <FileCode2 size={16} className="text-cyan-500" />
          {files.length} changed files
        </div>
        <div className="text-sm text-slate-500 dark:text-slate-400">Snapshot-aware diff preview</div>
      </div>

      {files.map((file) => {
        const open = openFiles[file.fileName] !== false
        return (
          <div key={file.fileName} className="overflow-hidden rounded-[20px] border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
            <button type="button" onClick={() => toggleFile(file.fileName)} className="flex w-full items-center justify-between border-b border-slate-200 bg-slate-50/90 px-4 py-3 text-left text-sm font-semibold text-slate-700 transition hover:bg-slate-100 dark:border-slate-800 dark:bg-slate-950/80 dark:text-slate-300 dark:hover:bg-slate-900">
              <span>{file.fileName}</span>
              <span className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                <PanelRightClose size={13} />
                {open ? 'Collapse' : 'Expand'}
                <ChevronDown size={14} className={`transition ${open ? 'rotate-180' : ''}`} />
              </span>
            </button>
            {open ? (
              <div className="grid gap-0 md:grid-cols-2">
                <div className="border-r border-slate-200 bg-rose-50/80 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-rose-950/20 dark:text-slate-300">
                  <div className="mb-2 text-[11px] font-semibold uppercase tracking-[0.2em] text-rose-600 dark:text-rose-300">Previous</div>
                  <pre className="whitespace-pre-wrap font-mono text-xs leading-6">{file.oldContent.join('')}</pre>
                </div>
                <div className="bg-emerald-50/80 p-4 text-sm text-slate-700 dark:bg-emerald-950/20 dark:text-slate-300">
                  <div className="mb-2 text-[11px] font-semibold uppercase tracking-[0.2em] text-emerald-600 dark:text-emerald-300">Current</div>
                  <pre className="whitespace-pre-wrap font-mono text-xs leading-6">{file.newContent.join('')}</pre>
                </div>
              </div>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

export default DiffViewer
