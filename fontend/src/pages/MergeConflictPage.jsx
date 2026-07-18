import { useMemo, useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import ConflictResolver from '../components/ConflictResolver'
import NotificationToast from '../components/NotificationToast'
import { useRepo } from '../context/RepoContext'

function MergeConflictPage() {
  const { repoId } = useParams()
  const { selectedRepo, selectRepository } = useRepo()
  const [conflicts, setConflicts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [dbLocked, setDbLocked] = useState(false)
  
  // Toast state
  const [toast, setToast] = useState(null)

  const repo = useMemo(() => {
    return selectedRepo?.id === repoId ? selectedRepo : null
  }, [repoId, selectedRepo])

  const showToast = (message, type = 'info') => {
    setToast({ message, type })
  }

  const loadConflicts = async () => {
    if (!selectedRepo?.conflicts || selectedRepo.conflicts.length === 0) {
      setConflicts([])
      return
    }
    setLoading(true)
    setError("")
    setDbLocked(false)
    try {
      const list = []
      for (const file of selectedRepo.conflicts) {
        const res = await fetch(`/api/conflict-details?file=${encodeURIComponent(file)}`)
        if (res.status === 503) {
          setDbLocked(true)
          showToast("H2 Database is locked by another process.", "warning")
          break
        }
        if (res.ok) {
          const data = await res.json()
          list.push({
            fileName: file,
            currentContent: [data.left || ''],
            incomingContent: [data.right || ''],
            ancestorContent: [data.ancestor || '']
          })
        } else {
          console.error(`Failed to load conflict details for ${file}`)
        }
      }
      if (!dbLocked) {
        setConflicts(list)
      }
    } catch (err) {
      setError("Failed to load conflict details")
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadConflicts()
  }, [selectedRepo?.conflicts])

  const handleResolveAction = async (fileName, resolution, customContent = null) => {
    try {
      setDbLocked(false)
      const url = `/api/action?cmd=resolve&file=${encodeURIComponent(fileName)}&resolution=${resolution}`
      const options = { method: "POST" }
      if (resolution === "custom" && customContent !== null) {
        options.body = customContent
      }
      const res = await fetch(url, options)
      
      if (res.status === 503) {
        setDbLocked(true)
        showToast("Action failed: Database is locked.", "error")
        return
      }

      if (res.ok) {
        showToast(`Resolved conflict for ${fileName} successfully!`, "success")
        if (selectRepository && selectedRepo) {
          await selectRepository(selectedRepo)
        }
      } else {
        const err = await res.json()
        showToast(err.error || "Failed to resolve conflict", "error")
      }
    } catch (err) {
      showToast(err.message || "Error resolving conflict", "error")
    }
  }

  if (!repo) {
    return <div className="text-slate-400">Repository not found.</div>
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-500">Merge conflicts</p>
        <h2 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{repo.name}</h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Review conflicting files before merging your branch.</p>
      </div>

      {dbLocked && (
        <div className="rounded-3xl border border-amber-300 bg-amber-50/50 p-6 shadow-sm dark:border-amber-500/30 dark:bg-amber-950/20">
          <div className="flex items-start gap-4">
            <div className="rounded-xl bg-amber-500/20 p-2 text-amber-600 dark:text-amber-400">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <div className="space-y-1">
              <h4 className="font-semibold text-amber-800 dark:text-amber-400">H2 Database Connection Locked</h4>
              <p className="text-sm text-amber-700/80 dark:text-amber-300/80 leading-relaxed">
                Another process (likely a CLI command run in your terminal or a concurrent sync operations) is holding the exclusive database lock.
              </p>
              <div className="mt-3 flex gap-2">
                <button
                  onClick={() => {
                    setDbLocked(false);
                    loadConflicts();
                  }}
                  className="rounded-lg bg-amber-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-amber-700 transition"
                >
                  Retry Connection
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-rose-300 bg-rose-50 p-4 text-rose-700">
          ❌ {error}
        </div>
      )}

      {loading ? (
        <div className="text-slate-500">Loading conflict details...</div>
      ) : conflicts.length === 0 ? (
        <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 text-sm text-slate-500 shadow-sm dark:border-slate-800 dark:bg-slate-900/70 dark:text-slate-400">
          No conflicts to resolve.
        </div>
      ) : (
        <div className="space-y-4">
          {conflicts.map((conflict) => (
            <ConflictResolver
              key={conflict.fileName}
              conflict={conflict}
              onAcceptCurrent={() => handleResolveAction(conflict.fileName, "ours")}
              onAcceptIncoming={() => handleResolveAction(conflict.fileName, "theirs")}
              onResolve={() => handleResolveAction(conflict.fileName, "both")}
              onCustomResolve={(content) => handleResolveAction(conflict.fileName, "custom", content)}
            />
          ))}
        </div>
      )}

      {toast && (
        <NotificationToast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  )
}

export default MergeConflictPage
