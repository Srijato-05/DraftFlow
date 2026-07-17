import { useMemo, useState } from 'react'
import {
  Activity,
  ArrowRight,
  BellRing,
  Clock3,
  FolderGit2,
  GitBranch,
  GitCommitVertical,
  Search,
  Sparkles,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useRepo } from '../context/RepoContext'

function RepoListPage({ user }) {
  const navigate = useNavigate()
  const { repositories, selectedRepo, setSelectedRepo, loading } = useRepo()
  const [query, setQuery] = useState('')

  const filteredRepos = useMemo(() => {
    const term = query.toLowerCase()
    return (repositories || []).filter((repo) => {
      const matchesSearch =
        repo.name.toLowerCase().includes(term) ||
        (repo.description || '').toLowerCase().includes(term) ||
        (repo.lastCommitMessage || '').toLowerCase().includes(term)

      return matchesSearch
    })
  }, [query, repositories])

  const recentSnapshots = filteredRepos.slice(0, 4)
  const latestCommit = selectedRepo?.commits?.[0]
  const currentBranch = selectedRepo?.currentBranch ?? 'main'
  const greeting = new Date().getHours() < 12 ? 'Good Morning' : new Date().getHours() < 18 ? 'Good Afternoon' : 'Good Evening'

  const quickActions = [
    { label: 'Open Repository', path: selectedRepo ? `/repo/${selectedRepo.id}` : '/repos' },
    { label: 'View Branches', path: selectedRepo ? `/repo/${selectedRepo.id}/branches` : '/repos' },
    { label: 'Review Merge', path: selectedRepo ? `/repo/${selectedRepo.id}/merge` : '/repos' },
  ]

  const recentActivity = useMemo(() => {
    if (!selectedRepo?.commits || selectedRepo.commits.length === 0) {
      return [{ title: 'No workspace activity yet', time: 'now', detail: 'Start committing to see history' }]
    }
    return selectedRepo.commits.slice(0, 3).map(c => ({
      title: `Saved snapshot: ${c.message}`,
      time: c.timestamp ? new Date(c.timestamp).toLocaleDateString() : 'recently',
      detail: `by ${c.author || 'Developer'} (${c.id.slice(0, 8)})`
    }))
  }, [selectedRepo?.commits])

  const notifications = useMemo(() => {
    const list = []
    if (selectedRepo?.conflicts && selectedRepo.conflicts.length > 0) {
      selectedRepo.conflicts.forEach(f => {
        list.push({ title: 'Conflict detected', detail: `${f} requires manual resolution` })
      })
    }
    const modifiedFiles = selectedRepo?.files?.filter(f => f.status === 'modified') || []
    if (modifiedFiles.length > 0) {
      list.push({ title: 'Changes pending', detail: `${modifiedFiles.length} file(s) modified. Use Save to snapshot.` })
    }
    const untrackedFiles = selectedRepo?.files?.filter(f => f.status === 'untracked') || []
    if (untrackedFiles.length > 0) {
      list.push({ title: 'Untracked files', detail: `${untrackedFiles.length} new file(s) found in repository` })
    }
    if (list.length === 0) {
      list.push({ title: 'Workspace up to date', detail: 'All files match the latest snapshot' })
    }
    return list.slice(0, 3)
  }, [selectedRepo])

  const handleOpenRepo = (repo) => {
    setSelectedRepo(repo)
    navigate(`/repo/${repo.id}`)
  }

  const handleQuickAction = (path) => {
    navigate(path)
  }

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <p className="text-slate-500">Loading workspace...</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="rounded-4xl border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70 sm:p-8">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.35em] text-cyan-500">Developer workspace</p>
            <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 dark:text-white sm:text-4xl">{greeting}, {user?.name?.split(' ')[0] ?? 'Developer'}</h2>
            <p className="mt-3 text-base leading-7 text-slate-500 dark:text-slate-400">
              Your recent snapshots, active repository context, and next actions are ready in one polished view.
            </p>
          </div>

          <label className="w-full max-w-md rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 shadow-sm dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-300">
            <div className="flex items-center gap-2">
              <Search size={16} className="text-cyan-500" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search snapshots"
                className="w-full bg-transparent outline-none placeholder:text-slate-400"
              />
            </div>
          </label>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <div className="space-y-6">
          <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">Recent Snapshots</p>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Latest repositories and their current state</p>
              </div>
              <span className="rounded-full bg-cyan-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.25em] text-cyan-500">
                Live
              </span>
            </div>

            <div className="mt-5 space-y-3">
              {recentSnapshots.map((repo) => (
                <button
                  key={repo.id}
                  type="button"
                  onClick={() => handleOpenRepo(repo)}
                  className="flex w-full items-center justify-between rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-left transition hover:border-cyan-500 hover:bg-white dark:border-slate-800 dark:bg-slate-950/70 dark:hover:border-cyan-500"
                >
                  <div>
                    <p className="font-semibold text-slate-900 dark:text-white">{repo.name}</p>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{repo.lastCommitMessage}</p>
                  </div>
                  <div className="text-right text-sm text-slate-500 dark:text-slate-400">
                    <p>{repo.currentBranch}</p>
                    <p className="mt-1">{repo.updatedAt}</p>
                  </div>
                </button>
              ))}
            </div>
          </section>

          <div className="grid gap-6 md:grid-cols-2">
            <section className="min-w-0 overflow-hidden rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <FolderGit2 size={18} className="text-cyan-500" />
                Current Repository
              </div>
              <div className="mt-4">
                <p className="text-2xl font-semibold text-slate-900 dark:text-white truncate">{selectedRepo?.name ?? 'No repository selected'}</p>
                <p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400 truncate">{selectedRepo?.description ?? 'Select a repository to see its details.'}</p>
              </div>
              <div className="mt-5 flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-300">
                <span>{selectedRepo?.visibility ?? 'Public'}</span>
                <button type="button" onClick={() => handleOpenRepo(selectedRepo)} className="font-semibold text-cyan-600 transition hover:text-cyan-500">
                  Open
                </button>
              </div>
            </section>

            <section className="min-w-0 overflow-hidden rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <GitCommitVertical size={18} className="text-cyan-500" />
                HEAD
              </div>
              <div className="mt-4">
                <p className="font-mono text-2xl font-bold tracking-tight text-slate-900 dark:text-white truncate">
                  {latestCommit?.id ? latestCommit.id.slice(0, 8) : 'N/A'}
                </p>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 truncate" title={latestCommit?.message}>
                  {latestCommit?.message ?? 'No recent commit'}
                </p>
                <p className="mt-1 font-mono text-[10px] text-slate-400 dark:text-slate-500 break-all" title={latestCommit?.id}>
                  {latestCommit?.id ?? ''}
                </p>
              </div>
            </section>
          </div>

          <div className="grid gap-6 md:grid-cols-2">
            <section className="min-w-0 overflow-hidden rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <Clock3 size={18} className="text-cyan-500" />
                Latest Commit
              </div>
              <div className="mt-4">
                <p className="text-lg font-semibold text-slate-900 dark:text-white truncate">{latestCommit?.message ?? 'No recent commit'}</p>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 truncate">{latestCommit?.author ?? 'Workspace activity'}</p>
              </div>
            </section>

            <section className="min-w-0 overflow-hidden rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <GitBranch size={18} className="text-cyan-500" />
                Branch
              </div>
              <div className="mt-4">
                <p className="text-xl font-semibold text-slate-900 dark:text-white truncate">{currentBranch}</p>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 truncate">{selectedRepo?.branches?.length ?? 0} branches available</p>
              </div>
            </section>
          </div>
        </div>

        <div className="space-y-6">
          <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
              <Sparkles size={18} className="text-cyan-500" />
              Quick Actions
            </div>
            <div className="mt-4 space-y-3">
              {quickActions.map((action) => (
                <button
                  key={action.label}
                  type="button"
                  onClick={() => handleQuickAction(action.path)}
                  className="flex w-full items-center justify-between rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm font-medium text-slate-700 transition hover:border-cyan-500 hover:bg-white dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-200"
                >
                  <span>{action.label}</span>
                  <ArrowRight size={16} className="text-cyan-500" />
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
              <Activity size={18} className="text-cyan-500" />
              Recent Activity
            </div>
            <div className="mt-4 space-y-3">
              {recentActivity.map((entry) => (
                <div key={entry.title} className="rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 dark:border-slate-800 dark:bg-slate-950/70">
                  <div className="flex items-center justify-between gap-3">
                    <p className="font-medium text-slate-900 dark:text-white">{entry.title}</p>
                    <span className="text-xs text-slate-500 dark:text-slate-400">{entry.time}</span>
                  </div>
                  <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{entry.detail}</p>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
              <BellRing size={18} className="text-cyan-500" />
              Notifications
            </div>
            <div className="mt-4 space-y-3">
              {notifications.map((item) => (
                <div key={item.title} className="rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 dark:border-slate-800 dark:bg-slate-950/70">
                  <p className="font-medium text-slate-900 dark:text-white">{item.title}</p>
                  <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{item.detail}</p>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}

export default RepoListPage