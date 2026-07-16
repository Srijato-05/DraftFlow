import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import DiffViewer from '../components/DiffViewer'
import { useRepo } from '../context/RepoContext'

function DiffViewPage() {
  const { repoId, commitId } = useParams()
  const { selectedRepo } = useRepo()
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const repo = useMemo(() => {
    return selectedRepo?.id === repoId ? selectedRepo : null
  }, [repoId, selectedRepo])

  const commit = useMemo(() => {
    return (selectedRepo?.commits || []).find(c => c.id === commitId) ?? null
  }, [commitId, selectedRepo])

  useEffect(() => {
    const fetchDiff = async () => {
      setLoading(true)
      setError("")
      try {
        const res = await fetch(`/api/commit-diff?commit=${encodeURIComponent(commitId)}`)
        if (res.ok) {
          const data = await res.json()
          setFiles(data)
        } else {
          setError("Failed to fetch commit diff")
        }
      } catch (err) {
        setError(err.message || "Failed to load commit diff")
      } finally {
        setLoading(false)
      }
    }
    fetchDiff()
  }, [commitId])

  if (!repo || !commit) {
    return <div className="text-slate-400">Commit details not found.</div>
  }

  if (loading) {
    return <div className="text-slate-500">Loading diff...</div>
  }

  if (error) {
    return <div className="text-rose-500">❌ {error}</div>
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-500">Diff view</p>
        <h2 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{commit.message}</h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          {commit.author} • {new Date(commit.timestamp).toLocaleString()}
        </p>
      </div>

      <DiffViewer files={files} />
    </div>
  )
}

export default DiffViewPage
