import { useEffect, useState } from 'react'
import { Monitor, MoonStar, ShieldCheck, Sparkles, SunMedium, GitBranch } from 'lucide-react'

function SettingsPage({ user, theme, onThemeChange }) {
  const [localTheme, setLocalTheme] = useState(theme ?? 'dark')
  
  const [requiresCodeReview, setRequiresCodeReview] = useState(false)
  const [requiresStatusChecks, setRequiresStatusChecks] = useState(false)
  const [dismissesStaleReviews, setDismissesStaleReviews] = useState(false)
  const [defaultBranch, setDefaultBranch] = useState("main")
  const [authorName, setAuthorName] = useState("")
  const [authorEmail, setAuthorEmail] = useState("")
  
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")

  useEffect(() => {
    setLocalTheme(theme ?? 'dark')
  }, [theme])

  const fetchSettings = async () => {
    try {
      const res = await fetch('/api/settings')
      if (res.ok) {
        const data = await res.json()
        setRequiresCodeReview(Boolean(data.requiresCodeReview))
        setRequiresStatusChecks(Boolean(data.requiresStatusChecks))
        setDismissesStaleReviews(Boolean(data.dismissesStaleReviews))
        setDefaultBranch(data.defaultBranch || "main")
        setAuthorName(data.authorName || "")
        setAuthorEmail(data.authorEmail || "")
      }
    } catch (err) {
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchSettings()
  }, [])

  const handleSaveSettings = async (e) => {
    if (e) e.preventDefault()
    setSaving(true)
    setMessage("")
    try {
      const res = await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          requiresCodeReview,
          requiresStatusChecks,
          dismissesStaleReviews,
          defaultBranch,
          authorName,
          authorEmail,
        })
      })
      if (res.ok) {
        setMessage("✅ Settings saved successfully!")
      } else {
        const errData = await res.json().catch(() => ({}))
        setMessage("❌ Failed to save settings: " + (errData.error || "Server error"))
      }
    } catch (err) {
      setMessage("❌ Error: " + err.message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <p className="text-slate-500">Loading settings...</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-500">Settings</p>
        <h2 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">Preferences</h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Tune the workspace to match your workflow.</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_0.95fr]">
        <div className="space-y-6">
          <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <ShieldCheck size={18} className="text-cyan-500" />
              Account
            </div>
            <div className="mt-4 space-y-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-400">
              <p>Signed in as <span className="font-semibold text-slate-800 dark:text-slate-200">{user?.name ?? 'Developer'}</span></p>
              <p>Email: {user?.email ?? 'dev@vcs.dev'}</p>
              <p className="text-xs uppercase tracking-[0.25em] text-slate-400">Workspace profile • Live session</p>
            </div>
          </div>

          <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
            <div className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <GitBranch size={18} className="text-cyan-500" />
              Branch Protection Policies
            </div>
            
            <form onSubmit={handleSaveSettings} className="mt-4 space-y-4">
              <label className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-950/60">
                <input
                  type="checkbox"
                  checked={requiresCodeReview}
                  onChange={(e) => setRequiresCodeReview(e.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-cyan-500"
                />
                <div>
                  <span className="block text-sm font-medium text-slate-800 dark:text-slate-200">Require pull request reviews before merging</span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">All commits must be made to a non-protected branch and submitted via pull request.</span>
                </div>
              </label>

              <label className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-950/60">
                <input
                  type="checkbox"
                  checked={requiresStatusChecks}
                  onChange={(e) => setRequiresStatusChecks(e.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-cyan-500"
                />
                <div>
                  <span className="block text-sm font-medium text-slate-800 dark:text-slate-200">Require status checks to pass before merging</span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">Choose which status checks must pass before branches can be merged.</span>
                </div>
              </label>

              <label className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-950/60">
                <input
                  type="checkbox"
                  checked={dismissesStaleReviews}
                  onChange={(e) => setDismissesStaleReviews(e.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-cyan-500"
                />
                <div>
                  <span className="block text-sm font-medium text-slate-800 dark:text-slate-200">Dismiss stale pull request approvals when new commits are pushed</span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">New commits pushed to a pull request branch will dismiss existing approvals.</span>
                </div>
              </label>

              <div>
                <label className="block text-sm font-medium text-slate-800 dark:text-slate-200 mb-2">Default Branch Name</label>
                <input
                  type="text"
                  value={defaultBranch}
                  onChange={(e) => setDefaultBranch(e.target.value)}
                  className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950"
                  placeholder="main"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-800 dark:text-slate-200 mb-2">Repository Author Name</label>
                <input
                  type="text"
                  value={authorName}
                  onChange={(e) => setAuthorName(e.target.value)}
                  className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950"
                  placeholder="Jane Doe"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-800 dark:text-slate-200 mb-2">Repository Author Email</label>
                <input
                  type="email"
                  value={authorEmail}
                  onChange={(e) => setAuthorEmail(e.target.value)}
                  className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950"
                  placeholder="jane@vcs.dev"
                />
              </div>

              <div className="flex items-center justify-between gap-4">
                <span className="text-sm font-medium text-cyan-600 dark:text-cyan-400">{message}</span>
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-full bg-cyan-500 px-5 py-2 text-sm font-semibold text-slate-950 transition hover:bg-cyan-400 disabled:opacity-50"
                >
                  {saving ? 'Saving...' : 'Save Settings'}
                </button>
              </div>
            </form>
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
          <div className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Sparkles size={18} className="text-cyan-500" />
            Appearance
          </div>
          <div className="mt-4 flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-950/70">
            <div>
              <p className="text-sm font-medium text-slate-800 dark:text-slate-200">Theme</p>
              <p className="text-sm text-slate-500 dark:text-slate-400">Switch between dark, light, and system modes.</p>
            </div>
            <button
              type="button"
              onClick={onThemeChange}
              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:border-cyan-500 hover:text-cyan-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              {localTheme === 'dark' ? <MoonStar size={14} /> : localTheme === 'light' ? <SunMedium size={14} /> : <Monitor size={14} />}
              {localTheme === 'dark' ? 'Dark' : localTheme === 'light' ? 'Light' : 'System'}
            </button>
          </div>
          <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-400">
            Repository preferences are synchronized with your Live workspace settings.
          </div>
        </div>
      </div>
    </div>
  )
}

export default SettingsPage
