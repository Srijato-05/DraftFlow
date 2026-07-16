import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FolderPlus } from "lucide-react";
import { useRepo } from "../context/RepoContext";

export default function CreateRepositoryPage() {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [visibility, setVisibility] = useState("Public");
  const [defaultBranch, setDefaultBranch] = useState("main");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const { refresh } = useRepo();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError("");
    setSuccess(false);

    try {
      const res = await fetch("/api/repositories/create", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          description,
          visibility: visibility.toLowerCase(),
          defaultBranch,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Failed to create repository");
      }

      setSuccess(true);
      setName("");
      setDescription("");
      setVisibility("Public");
      setDefaultBranch("main");

      if (refresh) refresh();

      setTimeout(() => {
        navigate("/repositories");
      }, 1500);
    } catch (err) {
      setError(err.message || "Failed to create repository");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">

      {/* Header */}

      <div>
        <h1 className="flex items-center gap-3 text-3xl font-bold">
          <FolderPlus className="text-cyan-500" size={32} />
          Create Repository
        </h1>

        <p className="mt-2 text-slate-500">
          Create a new Git repository.
        </p>
      </div>

      {/* Form */}

      <form
        onSubmit={handleSubmit}
        className="space-y-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-900"
      >

        <div>
          <label className="mb-2 block font-medium">
            Repository Name
          </label>

          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="my-awesome-project"
            className="w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-cyan-500"
          />
        </div>

        <div>
          <label className="mb-2 block font-medium">
            Description
          </label>

          <textarea
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Repository description..."
            className="w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-cyan-500"
          />
        </div>

        <div className="grid gap-5 md:grid-cols-2">

          <div>
            <label className="mb-2 block font-medium">
              Visibility
            </label>

            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value)}
              className="w-full rounded-xl border border-slate-300 p-3"
            >
              <option>Public</option>
              <option>Private</option>
            </select>
          </div>

          <div>
            <label className="mb-2 block font-medium">
              Default Branch
            </label>

            <select
              value={defaultBranch}
              onChange={(e) => setDefaultBranch(e.target.value)}
              className="w-full rounded-xl border border-slate-300 p-3"
            >
              <option>main</option>
              <option>master</option>
            </select>
          </div>

        </div>

        <button
          type="submit"
          disabled={loading}
          className="rounded-xl bg-cyan-500 px-6 py-3 font-medium text-white transition hover:bg-cyan-600 disabled:opacity-50"
        >
          {loading ? "Creating..." : "Create Repository"}
        </button>

        {success && (
          <div className="rounded-xl border border-green-300 bg-green-50 p-4 text-green-700">
            ✅ Repository Created Successfully! Redirecting...
          </div>
        )}

        {error && (
          <div className="rounded-xl border border-rose-300 bg-rose-50 p-4 text-rose-700">
            ❌ {error}
          </div>
        )}

      </form>

    </div>
  );
}