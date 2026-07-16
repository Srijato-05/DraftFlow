import { ArrowRight, GitCommitVertical, Layers3, Star } from 'lucide-react'

function RepoListItem({ repo, onOpen }) {
  return (
    <button type="button" onClick={() => onOpen(repo)} className="group flex w-full flex-col gap-3 rounded-[20px] border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-cyan-500 hover:shadow-lg dark:border-slate-800 dark:bg-slate-950/70">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold text-slate-900 transition group-hover:text-cyan-600 dark:text-white dark:group-hover:text-cyan-400">{repo.name}</h3>
            <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.24em] text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">{repo.visibility}</span>
          </div>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{repo.description}</p>
        </div>
        <div className="rounded-full border border-slate-200 bg-slate-50 p-2 text-slate-500 transition group-hover:border-cyan-500 group-hover:text-cyan-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          <Star size={16} />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 text-sm text-slate-500 dark:text-slate-400">
        <span className="inline-flex items-center gap-2 rounded-full bg-slate-50 px-2.5 py-1 dark:bg-slate-900/70">
          <GitCommitVertical size={14} />
          {repo.lastCommitMessage}
        </span>
        <span className="inline-flex items-center gap-2 rounded-full bg-slate-50 px-2.5 py-1 dark:bg-slate-900/70">
          <Layers3 size={14} />
          {repo.currentBranch}
        </span>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500 dark:text-slate-400">
        <span>Updated {repo.updatedAt}</span>
        <span className="inline-flex items-center gap-2 font-medium text-slate-700 dark:text-slate-300">
          Open repo
          <ArrowRight size={14} className="transition group-hover:translate-x-0.5" />
        </span>
      </div>
    </button>
  )
}

export default RepoListItem
