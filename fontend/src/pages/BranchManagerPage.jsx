import { useEffect, useMemo, useState } from 'react'
import { GitBranch, Plus } from 'lucide-react'
import { useParams } from 'react-router-dom'
import BranchList from '../components/BranchList'
import { useRepo } from '../context/RepoContext'

function BranchManagerPage() {
  const { repoId } = useParams()
  const { selectedRepo, switchBranch, fetchRepoDetails } = useRepo()
  const [branches, setBranches] = useState(() => selectedRepo?.branches ?? [])
  const [currentBranch, setCurrentBranch] = useState(() => selectedRepo?.currentBranch ?? 'main')
  const [newBranch, setNewBranch] = useState('')

  const repo = useMemo(() => {
    return selectedRepo?.id === repoId ? selectedRepo : null
  }, [repoId, selectedRepo])

  useEffect(() => {
    if (repo) {
      setBranches(repo.branches)
      setCurrentBranch(repo.currentBranch)
    }
  }, [repo])

  if (!repo) {
    return <div className="text-slate-400">Repository not found.</div>
  }

  const handleCreate = async (event) => {
    event.preventDefault()

    const branchName = newBranch.trim()
    if (!branchName) {
      return
    }

    try {
      const res = await fetch(`/api/action?cmd=branch&create=${encodeURIComponent(branchName)}`, {
        method: "POST"
      })
      if (res.ok) {
        setNewBranch('')
        if (fetchRepoDetails) {
          await fetchRepoDetails(repo)
        }
      } else {
        const err = await res.json()
        alert(err.error || "Failed to create branch")
      }
    } catch (err) {
      alert(err.message || "Error creating branch")
    }
  }

  const handleSwitch = async (branchName) => {
    try {
      await switchBranch(repo.id, branchName)
      setCurrentBranch(branchName)
    } catch (err) {
      alert(err.message || "Failed to switch branch")
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-500">Branches</p>
            <h2 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{repo.name}</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Switch branches and create follow-up work streams.</p>
          </div>
          <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-300">
            <GitBranch size={16} className="text-cyan-500" />
            Active: {currentBranch}
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <BranchList branches={branches} currentBranch={currentBranch} onSwitch={handleSwitch} />

        <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
          <h3 className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-600 dark:text-slate-400">Create branch</h3>
          <form className="mt-4 space-y-3" onSubmit={handleCreate}>
            <input
              value={newBranch}
              onChange={(event) => setNewBranch(event.target.value)}
              className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950/70 dark:text-white"
              placeholder="feature/new-work"
            />
            <button type="submit" className="flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-semibold text-white transition hover:bg-cyan-600 dark:bg-cyan-500 dark:text-slate-950">
              <Plus size={16} />
              Create branch
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default BranchManagerPage
