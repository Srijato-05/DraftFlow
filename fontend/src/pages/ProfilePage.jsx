import { useMemo, useState } from 'react'
import { CalendarDays, Github, Mail, MapPin, UserCircle2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useRepo } from '../context/RepoContext'

const fieldLabels = {
  name: 'Full Name',
  username: 'Username',
  email: 'Email',
  age: 'Age',
  country: 'Country',
  domain: 'Domain',
  experience: 'Experience',
  preferredTheme: 'Preferred Theme',
  joinedDate: 'Joined Date',
  repositoryCount: 'Repository Count',
  currentBranch: 'Current Branch',
  currentRepository: 'Current Repository',
}

function ProfilePage() {
  const { user, updateProfile } = useAuth()
  const { repositories, selectedRepo } = useRepo()
  const [isEditing, setIsEditing] = useState(false)
  const [form, setForm] = useState({
    name: user?.name || '',
    username: user?.username || '',
    email: user?.email || '',
    age: user?.age ?? '',
    country: user?.country || '',
    domain: user?.domain || '',
    experience: user?.experience || '',
    preferredTheme: user?.preferredTheme || 'dark',
  })

  const profileItems = useMemo(() => {
    if (!user) {
      return []
    }

    return [
      ['name', user.name],
      ['username', user.username],
      ['email', user.email],
      ['age', user.age || 'Not set'],
      ['country', user.country || 'Not set'],
      ['domain', user.domain || 'Not set'],
      ['experience', user.experience || 'Not set'],
      ['preferredTheme', user.preferredTheme],
      ['joinedDate', new Date(user.joinedDate).toLocaleDateString()],
      ['repositoryCount', repositories?.length ?? 0],
      ['currentBranch', selectedRepo?.currentBranch || 'detached'],
      ['currentRepository', selectedRepo?.name || 'No active repository'],
    ]
  }, [user, repositories, selectedRepo])

  if (!user) {
    return null
  }

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    updateProfile({
      name: form.name,
      username: form.username,
      email: form.email,
      age: form.age,
      country: form.country,
      domain: form.domain,
      experience: form.experience,
      preferredTheme: form.preferredTheme,
    })
    setIsEditing(false)
  }

  return (
    <div className="space-y-6">
      <div className="rounded-[28px] border border-slate-200/70 bg-slate-50/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-950/70">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-linear-to-br from-cyan-500 to-indigo-600 text-3xl font-semibold text-white">
              {user.name?.slice(0, 1).toUpperCase() || 'D'}
            </div>
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.35em] text-cyan-500">Developer profile</p>
              <h1 className="mt-2 text-3xl font-semibold text-slate-900 dark:text-white">{user.name}</h1>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">@{user.username}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setIsEditing((current) => !current)}
            className="rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-cyan-500 hover:text-cyan-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          >
            {isEditing ? 'Cancel' : 'Edit Profile'}
          </button>
        </div>
      </div>

      {isEditing ? (
        <form onSubmit={handleSubmit} className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/80">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Full Name</span>
              <input name="name" value={form.name} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Username</span>
              <input name="username" value={form.username} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Email</span>
              <input name="email" value={form.email} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Age</span>
              <input name="age" value={form.age} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Country</span>
              <input name="country" value={form.country} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Domain</span>
              <input name="domain" value={form.domain} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Experience</span>
              <input name="experience" value={form.experience} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950" />
            </label>
            <label className="text-sm text-slate-700 dark:text-slate-300">
              <span className="mb-2 block">Preferred Theme</span>
              <select name="preferredTheme" value={form.preferredTheme} onChange={handleChange} className="w-full rounded-2xl border border-slate-300 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950">
                <option value="dark">Dark</option>
                <option value="light">Light</option>
                <option value="system">System</option>
              </select>
            </label>
            {/* Read-only system values shown below, not editable as profile properties */}
          </div>

          <div className="mt-6 flex justify-end">
            <button type="submit" className="rounded-full bg-cyan-500 px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-cyan-400">
              Save Profile
            </button>
          </div>
        </form>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/80">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
            <UserCircle2 size={18} className="text-cyan-500" />
            Personal details
          </div>
          <dl className="mt-5 space-y-3 text-sm">
            {profileItems.slice(0, 8).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200/60 bg-slate-50/80 px-3 py-2 dark:border-slate-800 dark:bg-slate-950/60">
                <dt className="text-slate-500 dark:text-slate-400">{fieldLabels[key]}</dt>
                <dd className="font-medium text-slate-900 dark:text-white">{value}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="rounded-[28px] border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/80">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
            <Github size={18} className="text-cyan-500" />
            Workspace details
          </div>
          <dl className="mt-5 space-y-3 text-sm">
            {profileItems.slice(8).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200/60 bg-slate-50/80 px-3 py-2 dark:border-slate-800 dark:bg-slate-950/60">
                <dt className="text-slate-500 dark:text-slate-400">{fieldLabels[key]}</dt>
                <dd className="font-medium text-slate-900 dark:text-white">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      <div className="flex flex-wrap gap-3 rounded-[28px] border border-slate-200/70 bg-slate-50/80 p-4 text-sm text-slate-600 shadow-sm dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-300">
        <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 dark:bg-slate-900">
          <Mail size={16} className="text-cyan-500" />
          {user.email}
        </span>
        <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 dark:bg-slate-900">
          <MapPin size={16} className="text-cyan-500" />
          {user.country}
        </span>
        <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 dark:bg-slate-900">
          <CalendarDays size={16} className="text-cyan-500" />
          {new Date(user.joinedDate).toLocaleDateString()}
        </span>
      </div>
    </div>
  )
}

export default ProfilePage
