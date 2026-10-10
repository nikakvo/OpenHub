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

---

## Releases and downloads

A **release** is a published version of a project, usually with files to download
(APKs, zip modules, binaries).

- The releases list marks the newest stable one **Latest** and test versions
  **Pre-release**.
- Open a release and tap the **download button** to see its files with their size and
  how many times they were downloaded. Tap a file to download it.
- Downloads go to the phone's **Download** folder. The built-in downloader works on
  Wi-Fi, mobile data, roaming and VPN, and resumes after a lost connection.
- When a download finishes, the notification has **Install** (APK files) or **Open**,
  and **Share**, for example to send a module zip to your root manager or to a chat.

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

## Settings

| Setting | What it does |
|---|---|
| Choose Theme | Dark (default), Light teal, Light indigo or AMOLED dark (pure black, saves battery on OLED screens) |
| Theme Accent Color | The color of buttons, links and highlights (light blue by default) |
| Start Page | Which page opens when you start the app |
| Release notifications | Repositories you get release notifications for |
| Release check interval | How often those repositories are checked |
| Cache Controller | Show saved data immediately and refresh it from the network in the background. Faster, and pages still open offline |
| Use built-in downloader | On: OpenHub downloads files itself, over any network. Off: download links are handed to another app (browser, download manager) |
| Use Custom Tabs | Open web links in a tab inside the app instead of a separate browser |
| Disable loading network image | Do not load pictures and avatars on mobile data, to save data |

---

## Glossary

| Word | Meaning |
|---|---|
| Repository (repo) | A project: its files, history, issues and releases |
| Star | Like or bookmark a repository on GitHub |
| Watch | Get notifications about everything in a repository |
| Fork | Your own copy of someone else's repository |
| Branch | A separate line of work inside a repository (e.g. `main`, `dev`) |
| Tag | A name for a fixed point in history, usually a version like `v1.2` |
| Commit | One saved change to the files |
| Release | A published version, usually with files to download |
| Pre-release | A test version, not considered stable |
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
