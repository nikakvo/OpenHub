# OpenHub help

OpenHub is a GitHub client for Android. You can browse repositories, read code and
READMEs, follow releases, download builds, manage issues, look at GitHub Actions and
more, without opening a browser.

You do not need to know much about GitHub to use it. The **Glossary** at the end
explains the GitHub words used in the app.

---

## Getting started

### Logging in

There are two ways to log in:

- **Login with browser** opens GitHub in your browser. Log in there and approve
  OpenHub; you are sent back to the app automatically. (Only shown when the build
  has its own GitHub OAuth App.)
- **Sign in with token** works everywhere. A *personal access token* is like a
  password made only for apps. Tap **Create token**: GitHub opens with the right
  permissions already ticked. Press *Generate token*, copy it and paste it into
  OpenHub. The token is stored only on this phone.

### More than one account

Open the side menu, tap the small arrow next to your name and choose **Add account**.
Tap an account in the same list to switch to it. **Logout** removes the current
account from the phone.

---

## The side menu

| Item | What it shows |
|---|---|
| Profile | Your profile: followers, repositories, gists, activity |
| Notifications | GitHub notifications (mentions, reviews, issues you follow) |
| News | Activity of the people and repositories you follow |
| Issues | Issues you created, are assigned to or are mentioned in |
| Gists | Your gists (small shared files and snippets) |
| My repos | Your repositories |
| Starred repos | Repositories you starred |
| Bookmarks | Repositories and users you bookmarked inside OpenHub |
| Search | Search repositories and users |
| Trace | Repositories and users you looked at recently |
| Trending repos | What is popular on GitHub today, this week or this month |
| Repo collections | Lists of repositories picked by GitHub around a topic |
| Featured topics | Popular topics; open one to see its repositories |
| Global news | Public activity on GitHub |
| Settings | See **Settings** below |
| Help | This page |
| About | Version, license and credits |

The icon at the top right of list pages sorts or filters the list.
On long lists an **arrow button** appears at the bottom right: tap it to jump back
to the top.

---

## Repositories

A repository page has four tabs:

- **Info**: description, numbers (issues, stars, forks, watchers) and the README.
  Wide tables and code blocks in the README scroll sideways.
- **Files**: browse folders and open files. Back goes up one folder.
- **Commits**: the history of changes.
- **Activity**: recent events in the repository.

The buttons at the top: **star** (like / save it) and **branch** (switch to another
branch or tag). The **menu** (three dots) has:

| Menu item | What it does |
|---|---|
| Share / Open in browser / Copy clone URL | The usual |
| Watch | Get GitHub notifications for everything in the repository |
| Fork | Make your own copy of the repository |
| Latest release | Open the newest stable release directly |
| Notify about new releases | Get a phone notification when a new release comes out (see **Release notifications**) |
| Releases | All releases |
| Actions | Builds and automations (see **GitHub Actions**) |
| Wiki | The repository's wiki pages |
| Download source (zip) | The source code of the current branch |
| Bookmark | Save it in Bookmarks |

Tap the **Stargazers** or **Watchers** numbers to see who they are. Since July 2026
GitHub shows these lists only to the repository's owners and collaborators, so for
other repositories you will see a note instead.

### Adding and editing files

In repositories where you have write access, the **Files** tab works like the GitHub
website:

- The **+** button: **Create new file** (type a `/` in the name to make folders, e.g.
  `docs/notes.md`) or **Upload files** from the phone into the folder you are in.
  Several uploaded files become one commit. Files up to 25 MB; bigger files belong in
  a release.
- Long-press a file: **Edit file**, **Rename or move**, **Delete file**, **Download**.
  An open file has the same actions in its toolbar and menu.
- The editor shows the file in a monospace font; Markdown files also get **Write** and
  **Preview**. Text files up to 512 KB can be edited.
- The editor commits to the branch shown at the bottom (**Branch: main ▾**). Tap it to
  pick another branch; an open file is then loaded again from that branch.
- **Commit changes** asks for a commit message (and an optional description), then
  either commits directly to that branch, or creates a **new branch** and, if you like,
  starts a **pull request** from it. When the branch is not the default one, **Start a
  pull request into main** (the default branch) is offered too; if that branch already
  has an open pull request, the commit is added to it and it opens. File permissions
  such as executable scripts are kept.
- Changing files under `.github/workflows` needs a token with the `workflow` scope.
  Tokens made with **Create token** in this version have it; for an older token,
  create a new one.

### Repository settings and branches

**Settings** in the menu of a repository you administer works like its Settings page
on github.com:

- **General**: name (renaming asks first), description, website and topics, then
  **Save**. **Template repository** and the **Default branch**.
- **Features**: Issues, Wikis, Projects, Discussions.
- **Pull requests**: which merge methods are allowed (at least one), their default
  commit messages, suggesting branch updates, auto-merge, deleting head branches
  after a merge.
- **Commits**: require sign-off on commits made on the website. **Releases**: release
  immutability (when GitHub offers it for the repository).
- **Danger zone**: change visibility, archive or unarchive, transfer ownership and
  **delete** the repository (you type its full name to confirm). Deleting needs a
  token with the `delete_repo` scope; **Create token** asks for it. For a token you
  already use, open it on github.com (Settings, Developer settings, Personal access
  tokens), tick `delete_repo` and press *Update token*: the token stays the same. If
  you signed in with the browser, log out and use **Login with browser** again; GitHub
  asks you to allow the new permissions.

**Branches** (in the repository menu, and in Settings) lists all branches with the
default and protected ones marked. With write access: **+** creates a branch from
another one, tap a branch to **rename** or **delete** it. Admins also get **Branch
protection**: require a pull request (with a number of approvals), conversation
resolution, linear history, allow or forbid force pushes and deletions, and apply
the rules to admins too. Without that last option you, as the owner, can still
commit directly to a protected branch. Protecting branches of private repositories needs a paid
GitHub plan.

### Creating a repository

**New repository** in the side menu works like github.com/new:

- **Owner**: your account, or one of your organizations (tap to switch).
- **Repository name**: OpenHub checks while you type whether the name is still free.
  GitHub allows letters, digits, `-`, `_` and `.`; other characters become `-`, and the
  form shows the final name.
- **Description**, **Public** or **Private**.
- **Initialize this repository with**: a README file, a **.gitignore** and a
  **License**. Leave them all off to get an empty repository you can push to.
- **.gitignore** lists files git should not track (build output, local settings).
  Search the templates and tick one or more (for example *Android* and *Gradle*), and
  add your own lines such as `*.apk` or `local.properties`. Several templates and own
  lines are combined into one `.gitignore` file.

After **Create repository** the new repository opens.

---

## Releases and downloads

A **release** is a published version of a project, usually with files to download
(APKs, zip modules, binaries).

- The releases list marks the newest stable one **Latest**, test versions
  **Pre-release** and unpublished ones **Draft** (only people with write access see
  drafts).
- Open a release to read its notes and see its files (**Assets**) with their size and
  how many times they were downloaded. Tap a file to download it, long-press it to
  copy its link. The **download button** also offers the source code archives.
- Downloads go to the phone's **Download** folder. The built-in downloader works on
  Wi-Fi, mobile data, roaming and VPN, and resumes after a lost connection.
- When a download finishes, the notification has **Install** (APK files) or **Open**,
  and **Share**, for example to send a module zip to your root manager or to a chat.

### Publishing your own releases

In repositories where you have write access, OpenHub can manage releases like the
GitHub website does.

- **New release**: the **+** button in the releases list. Enter a **tag** (pick an
  existing one with the tag button, or type a new one such as `v1.2.0`; a new tag is
  created from the **Target** branch when the release is published), a title and the
  release notes. **Generate release notes** lets GitHub write the notes from the merged
  pull requests since the previous release. **Write** and **Preview** above the notes
  switch between the text and how it will look on GitHub (rendered by GitHub itself,
  so `#12`, `@user` and commit hashes become links).
- **Add files** attaches files from the phone, for example an APK or a module zip you
  built in Termux or a Linux container (Termux appears in the file picker's side
  menu). The **pencil** changes the name a file gets on GitHub, the **cross** removes
  it.
- **Save draft** keeps the release private. **Publish release** makes it public. With
  new files, the release stays a draft while they upload and is published only when
  every file arrived, so nobody downloads an incomplete release. This also works for
  repositories with **release immutability** turned on, where files cannot change
  after publishing.
- **From other apps**: share one or more files (from a file manager, Termux with
  `termux-share`, Telegram...) and choose **Upload to GitHub release**. Pick the
  repository (the last used one is on top, with search), then an existing release or
  **New release**. Files that already exist in the release can be replaced.
- Uploads run in the background with a progress notification and **Cancel**; a lost
  connection is retried automatically. You can leave the app meanwhile.
- In an open release, the menu has **Edit**, **Publish release** (drafts), **Set as
  latest release**, **Upload files** and **Delete release** (optionally together with
  its tag). Each file has a **pencil** to rename it and a menu to copy its link or
  delete it.

Writing to releases needs a token with the `repo` scope (see **Logging in**).

---

## GitHub Actions

Many projects build their apps automatically with GitHub Actions, and the newest test
builds are often only there, not in releases. Open a repository's menu and choose
**Actions**:

- Each row is one **run**: what started it, the workflow name, the run number, the
  branch and its status (green Success, red Failed, yellow In progress / Queued,
  grey Cancelled / Skipped).
- **Tap a run** to see its **artifacts** (the files it built). Tap one, or its arrow,
  to download it as a zip. GitHub deletes artifacts after some time (usually 90 days);
  old ones are shown as *expired*.
- **Long tap a run** to open it in the browser (logs, details).
- The **filter icon** shows only one workflow. In the menu, **Successful runs only**
  hides failed runs.
- In your own repositories the **play button** starts a workflow by hand. Pick the
  workflow, check the branch and add inputs if the workflow needs them, one
  `key=value` per line. Only workflows that have a `workflow_dispatch` trigger can be
  started; otherwise GitHub's message tells you why it refused.

---

## GitHub notifications

**Notifications** in the side menu is your GitHub inbox, grouped by repository, with
the tabs **Unread**, **Participating** (threads you take part in) and **All**. Each
entry shows why you got it: *mentioned you*, *review requested*, *assigned to you*...

- Tap an entry to open it in the app (issues, pull requests, commits, workflow runs;
  releases and discussions open their page). It is marked as read.
- Long-press an entry for **Mark as read**, **Mark as done** (removes it from the
  inbox, like on github.com), **Unsubscribe** (no more notifications for that issue or
  pull request) and **Open in browser**.
- The check mark next to a repository marks all its notifications as read; the menu of
  the Unread tab marks everything as read.

**Notifications on the phone:** turn on **Settings > GitHub notifications** and OpenHub
checks your inbox in the background and shows new unread notifications, also when the
app is closed. Each has **Mark as read**; tap it to open the thread. **Settings > GitHub
notifications check** sets how often (15 minutes to 8 hours; 30 minutes by default).
Only what arrives after you turned it on is shown.

---

## Release notifications

Choose **Notify about new releases** in any repository's menu. OpenHub then checks
that repository in the background and shows a notification when a new release is
published. Tap the notification to open the release.

- **Settings > Release notifications** lists the repositories you follow this way.
  Uncheck one to stop, or use **Check now**.
- **Settings > Release check interval** sets how often to check (from 15 minutes to
  7 days; 6 hours by default). Checks need an internet connection and Android may
  delay them a little to save battery.
- Android 13 and newer ask for permission to show notifications the first time.

---

## Gists

A **gist** is a small set of files shared on GitHub: a script, a config, notes.
Open them from **Gists** in the side menu (yours, including secret ones) or from
anyone's profile. Tap a gist to see its files, tap a file to read it (Markdown is
formatted, code is highlighted) or tap its arrow to download it. Long tap a gist to
open it in the browser.

---

## Issues

Issues are bug reports and requests. Open them from a repository's **Info** tab or
from **Issues** in the side menu. You can comment (with a Markdown editor and
preview), edit, close and reopen issues, and manage labels in your own repositories.

---

## Pull requests

A pull request proposes merging the changes of one branch into another. Open
**Pulls** on a repository's **Info** tab (it shows how many are open) or **Pull
requests** from the repository's menu. Links to pull requests (in
notifications, issues, READMEs) and pull requests you create while committing a
file now open in the app instead of the browser.

The list has **Open** and **Closed** tabs (merged ones are shown as Merged). Tap a
pull request to see:

- **Conversation** - the description, comments and reviews, and the status of the
  checks (GitHub Actions and other CI)
- **Commits** - the commits it adds
- **Files** - the changed files (with the number of comments); tap one for its diff

At the bottom you can **Comment** or **Review changes** (Approve, Request changes or just
comment; Write and Preview like on the site). If you can write to the repository,
the merge box lets you:

- **Merge** with any method the repository allows (merge commit, squash, rebase),
  with your own title and message
- **Ready for review** for a draft, or **Convert to draft**
- **Delete branch** after merging or closing, and **Restore branch** if you change
  your mind
- **Close pull request** or **Reopen pull request**

The menu has **Edit** (title, description, base branch), Share, Copy link and Open
in browser.

### Commenting on lines

In the diff of a file, tap a line to comment on it, like on the site. To comment on
several lines, long-press the first line (it turns yellow), then tap the last one;
tap the same line again to cancel. On added or unchanged lines **Suggest a change**
inserts a suggestion block with the code: edit it, and the author can apply it on
github.com with one tap.

Comments appear under their line, with **Reply…** to answer the thread. Comments on
code that has changed since are listed at the top. Line comments also appear in
**Conversation** with the file and line; tap one to open the diff there.

Long-press a comment (in the diff or in Conversation) to **Edit comment** or **Delete
comment** (your own) or to copy its link.

### Opening a pull request

Tap **+** in the list, pick the base branch (where the changes go) and the compare
branch (where they come from), check the summary of commits and files, write a
title and description and tap **Create pull request**. In a fork, the pull request goes to the
original repository by default. Tick **Create as draft** if it is not ready yet.

The changes must be in a second branch. If the repository has only one branch, the
screen says so and offers **Branches**; the easiest way is to edit a file and fill in
**New branch name** when you commit (tick **Start a pull request** to create it right away).

---

## Settings

| Setting | What it does |
|---|---|
| Choose Theme | Dark (default), Light teal, Light indigo or AMOLED dark (pure black, saves battery on OLED screens) |
| Theme Accent Color | The color of buttons, links and highlights (light blue by default) |
| Start Page | Which page opens when you start the app |
| Release notifications | Repositories you get release notifications for |
| Release check interval | How often those repositories are checked |
| GitHub notifications | Off (default). On: new notifications from your GitHub inbox appear on the phone, also when the app is closed |
| GitHub notifications check | How often the inbox is checked (15 minutes to 8 hours) |
| Cache Controller | Show saved data immediately and refresh it from the network in the background. Faster, and pages still open offline |
| Use built-in downloader | On: OpenHub downloads files itself, over any network. Off: download links are handed to another app (browser, download manager) |
| Use Custom Tabs | Open web links in a tab inside the app instead of a separate browser |
| Open GitHub links in OpenHub | Shows whether github.com links from other apps (Telegram, mail, a browser) open in OpenHub. Since Android 12 you allow it once: tap it, turn on **Open supported links**, tap **Add link** and tick `github.com` |
| Disable loading network image | Do not load pictures and avatars on mobile data, to save data |
| Check for updates | On (default): when a new OpenHub version is released, the app says so at start (at most every 6 hours) |
| Check for updates now | Check right away; shows the installed version |

When an update is offered, **Download** fetches the new APK with the built-in
downloader; tap **Install** in its notification. **Skip this version** stops the
reminder for that version (Check for updates now still shows it).

---

## Glossary

| Word | Meaning |
|---|---|
| Repository (repo) | A project: its files, history, issues and releases |
| Star | Like or bookmark a repository on GitHub |
| Watch | Get notifications about everything in a repository |
| Fork | Your own copy of someone else's repository |
| Branch | A separate line of work inside a repository (e.g. `main`, `dev`) |
| Protected branch | A branch with rules, e.g. no force pushes, changes only through pull requests |
| Tag | A name for a fixed point in history, usually a version like `v1.2` |
| Commit | One saved change to the files |
| Release | A published version, usually with files to download |
| Pre-release | A test version, not considered stable |
| Draft | A release that is not published yet; only people with write access see it |
| Asset | A file attached to a release |
| Issue | A bug report, question or request |
| Pull request | A proposed change from someone else, waiting to be merged |
| Workflow | An automation in GitHub Actions, e.g. "Build APK" |
| Run | One execution of a workflow |
| Artifact | A file built by a run, kept for a limited time |
| Gist | A small set of shared files or snippets |
| Token | A password for apps, created on GitHub, that can be revoked at any time |

---

## Privacy

OpenHub talks only to GitHub. Your login token stays on this phone and is sent only to
GitHub's API. Logging out removes it. You can also revoke tokens and app access any
time in GitHub's settings (Settings > Applications, and Developer settings > Personal
access tokens).
