import { useMemo, useState } from 'react'
import { ChevronRight, FileCode2, Folder, FolderOpen, Search, Sparkles, GitBranch } from 'lucide-react'

function buildTree(entries) {
  const root = { name: 'root', type: 'folder', path: '', children: [] }

  entries.forEach((entry, index) => {
    const segments = entry.path.split('/')
    let current = root

    segments.forEach((segment, segmentIndex) => {
      const isFile = segmentIndex === segments.length - 1 && entry.type === 'file'
      const childPath = segments.slice(0, segmentIndex + 1).join('/')
      let child = current.children.find((item) => item.name === segment)

      if (!child) {
        child = {
          name: segment,
          type: isFile ? 'file' : 'folder',
          path: childPath,
          children: [],
          index,
          file: isFile ? entry : null,
        }
        current.children.push(child)
      }

      current = child
    })
  })

  const sortNodes = (node) => {
    node.children.sort((a, b) => {
      if (a.type === b.type) {
        return a.name.localeCompare(b.name)
      }

      return a.type === 'folder' ? -1 : 1
    })

    node.children.forEach(sortNodes)
  }

  sortNodes(root)
  return root.children
}

function getBadge(entry, index) {
  const normalized = (entry.path || '').toLowerCase()

  if (normalized.includes('readme') || normalized.includes('package') || normalized.includes('vite')) {
    return { label: 'Added', tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400' }
  }

  if (normalized.includes('auth') || normalized.includes('src') || normalized.includes('component')) {
    return { label: 'Modified', tone: 'bg-amber-500/12 text-amber-600 dark:text-amber-400' }
  }

  if (normalized.includes('legacy') || normalized.includes('old')) {
    return { label: 'Deleted', tone: 'bg-rose-500/12 text-rose-600 dark:text-rose-400' }
  }

  if (index % 3 === 0) {
    return { label: 'Modified', tone: 'bg-amber-500/12 text-amber-600 dark:text-amber-400' }
  }

  if (index % 3 === 1) {
    return { label: 'Added', tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400' }
  }

  return { label: 'Current snapshot', tone: 'bg-cyan-500/12 text-cyan-600 dark:text-cyan-400' }
}

function FileTree({ files, stagedFiles = [], onStage, onUnstage, showSearch = false, title = 'Changes', emptyMessage = 'No files available' }) {
  const [searchValue, setSearchValue] = useState('')
  const [expandedFolders, setExpandedFolders] = useState(() => new Set(['src']))

  const tree = useMemo(() => buildTree(files), [files])

  const filterTree = (nodes, query) => {
    if (!query) {
      return nodes
    }

    return nodes.reduce((acc, node) => {
      const matches = node.name.toLowerCase().includes(query.toLowerCase()) || (node.path || '').toLowerCase().includes(query.toLowerCase())
      const children = node.children ? filterTree(node.children, query) : []

      if (matches || children.length > 0) {
        acc.push({ ...node, children })
      }

      return acc
    }, [])
  }

  const visibleTree = useMemo(() => filterTree(tree, searchValue), [tree, searchValue])

  const toggleFolder = (path) => {
    setExpandedFolders((current) => {
      const next = new Set(current)
      if (next.has(path)) {
        next.delete(path)
      } else {
        next.add(path)
      }
      return next
    })
  }

  const renderNode = (node, depth = 0) => {
    const isExpanded = expandedFolders.has(node.path)
    const isFolder = node.type === 'folder'
    const isStaged = stagedFiles.some((item) => item.name === node.name)
    const badge = getBadge(node, node.index ?? 0)

    if (isFolder) {
      const hasVisibleChildren = node.children?.some((child) => child.children?.length > 0 || child.name.toLowerCase().includes(searchValue.toLowerCase()))
      return (
        <div key={node.path || node.name} className="space-y-1">
          <button
            type="button"
            onClick={() => toggleFolder(node.path)}
            className="flex w-full items-center justify-between rounded-xl border border-transparent px-2 py-2 text-left transition hover:border-slate-200 hover:bg-slate-50 dark:hover:border-slate-700 dark:hover:bg-slate-950/70"
          >
            <div className="flex min-w-0 items-center gap-2">
              {isExpanded ? <FolderOpen size={16} className="text-cyan-500" /> : <Folder size={16} className="text-slate-500" />}
              <span className="truncate text-sm font-medium text-slate-700 dark:text-slate-200">{node.name}</span>
            </div>
            <ChevronRight size={14} className={`text-slate-400 transition ${isExpanded ? 'rotate-90' : ''}`} />
          </button>
          {isExpanded && node.children?.length > 0 ? (
            <div className="ml-4 space-y-1 border-l border-slate-200 pl-3 dark:border-slate-800">
              {node.children.map((child) => renderNode(child, depth + 1))}
            </div>
          ) : null}
          {!hasVisibleChildren && searchValue ? <p className="ml-4 text-xs text-slate-400">No matches</p> : null}
        </div>
      )
    }

    return (
      <div key={node.path || node.name} className="flex items-center justify-between rounded-xl border border-slate-200/70 bg-white/80 px-3 py-2.5 shadow-sm transition hover:border-cyan-400 dark:border-slate-800 dark:bg-slate-900/80">
        <div className="flex min-w-0 items-center gap-2">
          <FileCode2 size={15} className="text-slate-500" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{node.name}</p>
            <p className="truncate text-xs text-slate-500 dark:text-slate-400">{node.path}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className={`rounded-full px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.2em] ${badge.tone}`}>
            {badge.label}
          </span>
          {isStaged ? <span className="rounded-full bg-emerald-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-emerald-600 dark:text-emerald-400">Staged</span> : null}
        </div>
      </div>
    )
  }

  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <GitBranch size={16} className="text-cyan-500" />
          <h3 className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-600 dark:text-slate-300">{title}</h3>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-400">
          <Sparkles size={12} className="text-cyan-500" />
          {files.length} items
        </div>
      </div>

      {showSearch ? (
        <label className="mt-4 flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500 shadow-sm dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-400">
          <Search size={15} className="text-cyan-500" />
          <input
            value={searchValue}
            onChange={(event) => setSearchValue(event.target.value)}
            placeholder="Search files"
            className="w-full bg-transparent outline-none"
          />
        </label>
      ) : null}

      <div className="mt-4 space-y-2">
        {visibleTree.length > 0 ? visibleTree.map((node) => renderNode(node)) : <p className="rounded-2xl border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">{emptyMessage}</p>}
      </div>
    </div>
  )
}

export default FileTree
