import { ArrowRight, GitBranch, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'

function WelcomePage({ user }) {
  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(34,211,238,0.16),transparent_24%),linear-gradient(135deg,#020617_0%,#111827_100%)] px-4 py-12 text-slate-100 sm:px-6 lg:px-8">
      <div className="mx-auto flex max-w-5xl flex-col gap-6 rounded-[28px] border border-white/10 bg-white/10 p-8 shadow-[0_30px_90px_rgba(0,0,0,0.45)] backdrop-blur-xl">
        <div className="rounded-3xl border border-cyan-400/20 bg-cyan-400/10 p-6">
          <div className="inline-flex items-center gap-2 rounded-full border border-cyan-400/20 bg-slate-950/50 px-3 py-1 text-sm font-medium text-cyan-300">
            <Sparkles size={16} />
            Welcome aboard
          </div>
          <h1 className="mt-4 text-3xl font-semibold tracking-tight text-white sm:text-4xl">Welcome, {user?.name ?? 'Developer'} 👋</h1>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300 sm:text-base">Let&apos;s personalize your workspace and get you into a smoother, more focused version control flow.</p>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1fr_0.9fr]">
          <div className="space-y-4 rounded-3xl border border-white/10 bg-slate-950/70 p-6">
            <h2 className="text-xl font-semibold text-white">Choose your role</h2>
            <div className="flex flex-wrap gap-2">
              {['Student', 'Individual Developer', 'Team', 'Enterprise'].map((role) => (
                <button key={role} type="button" className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300 transition hover:border-cyan-400 hover:text-cyan-300">
                  {role}
                </button>
              ))}
            </div>

            <h2 className="text-xl font-semibold text-white">How many repositories?</h2>
            <div className="flex flex-wrap gap-2">
              {['1–10', '10–50', '50+'].map((range) => (
                <button key={range} type="button" className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300 transition hover:border-cyan-400 hover:text-cyan-300">
                  {range}
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-3xl border border-white/10 bg-slate-950/70 p-6">
            <h2 className="text-xl font-semibold text-white">Import from</h2>
            <div className="mt-4 space-y-3">
              {['GitHub', 'GitLab', 'Bitbucket'].map((provider) => (
                <button key={provider} type="button" className="flex w-full items-center justify-between rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-left text-sm text-slate-300 transition hover:border-cyan-400 hover:text-cyan-300">
                  <span>{provider}</span>
                  <span className="text-cyan-300">Import</span>
                </button>
              ))}
              <Link to="/repos" className="mt-4 inline-flex items-center justify-center gap-2 rounded-2xl bg-cyan-500 px-4 py-3 text-center text-sm font-semibold text-slate-950 transition hover:bg-cyan-400">
                Continue to workspace
                <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default WelcomePage
