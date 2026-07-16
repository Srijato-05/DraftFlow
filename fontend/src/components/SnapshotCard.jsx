import {
  FolderGit2,
  GitCommit,
  CalendarDays,
  GitBranch,
} from "lucide-react";
import { Link } from "react-router-dom";

export default function SnapshotCard({ snapshot }) {
  if (!snapshot) return null;

  return (
    <Link
      to={`/repo/${snapshot.repositoryId}/snapshot/${snapshot.snapshotId}`}
      className="block"
    >
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:shadow-xl dark:border-slate-700 dark:bg-slate-900">

        {/* Header */}
        <div className="flex items-start justify-between">

          <div>

            <div className="flex items-center gap-2">
              <FolderGit2
                className="text-cyan-500"
                size={22}
              />

              <h2 className="text-xl font-semibold">
                {snapshot.snapshotId}
              </h2>
            </div>

            <p className="mt-2 text-slate-500">
              {snapshot.message}
            </p>

          </div>

          <span className="rounded-full bg-cyan-500/10 px-3 py-1 text-sm font-medium text-cyan-600 dark:text-cyan-400">
            {snapshot.branch}
          </span>

        </div>

        {/* Information */}
        <div className="mt-6 grid gap-5 md:grid-cols-4">

          <div>
            <div className="flex items-center gap-2">
              <GitCommit size={16} />
              <span className="text-sm text-slate-500">
                Commit
              </span>
            </div>

            <p className="mt-1 font-medium">
              {snapshot.commitId}
            </p>
          </div>

          <div>
            <div className="flex items-center gap-2">
              <FolderGit2 size={16} />
              <span className="text-sm text-slate-500">
                Tree
              </span>
            </div>

            <p className="mt-1 font-medium">
              {snapshot.treeId}
            </p>
          </div>

          <div>
            <div className="flex items-center gap-2">
              <CalendarDays size={16} />
              <span className="text-sm text-slate-500">
                Created
              </span>
            </div>

            <p className="mt-1 font-medium">
              {snapshot.createdAt}
            </p>
          </div>

          <div>
            <div className="flex items-center gap-2">
              <GitBranch size={16} />
              <span className="text-sm text-slate-500">
                Repository
              </span>
            </div>

            <p className="mt-1 font-medium">
              {snapshot.repositoryId}
            </p>
          </div>

        </div>

      </div>
    </Link>
  );
}