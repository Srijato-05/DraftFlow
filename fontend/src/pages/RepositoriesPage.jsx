import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FolderGit2, Search, GitBranch, GitCommit, Users, Globe, Lock, ArrowRight, ArrowLeft,} from "lucide-react";
import { useRepo } from "../context/RepoContext";

export default function RepositoriesPage() {
  const [search, setSearch] = useState("");
  const navigate = useNavigate();
  const { repositories, loading } = useRepo();

  const filteredRepositories = useMemo(() => {
    return (repositories || []).filter((repo) =>
      repo.name.toLowerCase().includes(search.toLowerCase())
    );
  }, [repositories, search]);

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <p className="text-slate-500">Loading repositories...</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
        <button onClick={() => navigate(-1)}
           className="mb-4 inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-cyan-500 hover:text-cyan-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
         <ArrowLeft size={18} />
          Back
        </button>

      {/* Header */}

      <div>
        <h1 className="flex items-center gap-3 text-3xl font-bold text-slate-900 dark:text-white">
          <FolderGit2 className="text-cyan-500" size={32} />
          Repositories
        </h1>

        <p className="mt-2 text-slate-500 dark:text-slate-400">
          Browse all repositories in your Snapshot VCS.
        </p>
      </div>

      {/* Search */}

      <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 dark:border-slate-700 dark:bg-slate-900">
        <Search size={18} className="text-slate-400" />

        <input
          type="text"
          placeholder="Search repositories..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full bg-transparent outline-none"
        />
      </div>

      {/* Repository Cards */}

      <div className="space-y-5">

        {filteredRepositories.map((repo) => (

          <div
            key={repo.id}
            className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-lg dark:border-slate-700 dark:bg-slate-900"
          >

            {/* Top */}

            <div className="flex items-start justify-between">

              <div>

                <h2 className="flex items-center gap-2 text-xl font-semibold">

                  <FolderGit2
                    className="text-cyan-500"
                    size={22}
                  />

                  {repo.name}

                </h2>

                <p className="mt-2 text-slate-500">
                  {repo.description}
                </p>

              </div>

              <span
                className={`rounded-full px-3 py-1 text-sm font-medium ${
                  repo.visibility === "public"
                    ? "bg-emerald-100 text-emerald-700"
                    : "bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
                }`}
              >
                {repo.visibility === "public" ? (
                  <span className="flex items-center gap-1">
                    <Globe size={14} />
                    Public
                  </span>
                ) : (
                  <span className="flex items-center gap-1">
                    <Lock size={14} />
                    Private
                  </span>
                )}
              </span>

            </div>

            {/* Statistics */}

            <div className="mt-6 grid gap-5 md:grid-cols-4">

              <div>
                <div className="flex items-center gap-2">
                  <GitBranch size={16} />
                  <span className="text-sm text-slate-500">
                    Branch
                  </span>
                </div>

                <p className="mt-1 font-medium">
                  {repo.defaultBranch || "main"}
                </p>
              </div>

              <div>
                <div className="flex items-center gap-2">
                  <GitCommit size={16} />
                  <span className="text-sm text-slate-500">
                    Commits
                  </span>
                </div>

                <p className="mt-1 font-medium">
                  {repo.statistics?.totalCommits ?? 0}
                </p>
              </div>

              <div>
                <div className="flex items-center gap-2">
                  <Users size={16} />
                  <span className="text-sm text-slate-500">
                    Contributors
                  </span>
                </div>

                <p className="mt-1 font-medium">
                  {repo.statistics?.totalContributors ?? 0}
                </p>
              </div>

              <div>
                <div className="flex items-center gap-2">
                  <FolderGit2 size={16} />
                  <span className="text-sm text-slate-500">
                    Updated
                  </span>
                </div>

                <p className="mt-1 font-medium">
                  {repo.updatedAt || "Just now"}
                </p>
              </div>

            </div>

            {/* Button */}

            <div className="mt-6 flex justify-end">

              <Link
                to={`/repo/${repo.id}`}
                className="inline-flex items-center gap-2 rounded-xl bg-cyan-500 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-cyan-600"
               >
                Open Repository
                <ArrowRight size={16} />
              </Link>

            </div>

          </div>

        ))}

      </div>

    </div>
  );
}