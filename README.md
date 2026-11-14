<p align="center"><img src="openhub.png" alt="OpenHub"></p>

# OpenHub

[![Release](https://img.shields.io/github/v/release/nikakvo/OpenHub?include_prereleases)](https://github.com/nikakvo/OpenHub/releases)
[![Tests](https://github.com/nikakvo/OpenHub/actions/workflows/tests.yml/badge.svg)](https://github.com/nikakvo/OpenHub/actions/workflows/tests.yml)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)](#requirements)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

An open-source GitHub client for Android that reads and writes: browse repositories and
code, edit files with real commits, publish releases, review and merge pull requests,
follow builds and get your notifications on the phone.

This is a modernized fork of [OpenHub](https://github.com/ThirtyDegreesRay/OpenHub) by
**ThirtyDegreesRay**, based on the maintained fork by
[**yxj-github-437**](https://github.com/yxj-github-437/OpenHub). All credit for the original
app goes to them; see [Credits](#credits).

## Compared with the original OpenHub

* **Android 17 ready**: targets API 37, edge-to-edge, predictive back gesture.
* **Sign in with a personal access token**, so the app works without its own OAuth App.
  Browser login (OAuth) is still there when the build has one.
* **GitHub Actions** for any repository: workflow runs with their status, artifacts with
  download buttons, filter by workflow, and starting workflows by hand in your own
  repositories.
* **Releases**: open the latest release directly, Latest / Pre-release badges, file sizes
  and download counts, Install / Open / Share right from the download notification.
* **Release notifications**: follow any repository and get a notification when a new
  release comes out, with an adjustable check interval.
* **Gists**: your gists and anyone else's, with formatted Markdown and highlighted code. Create gists (public or secret), add, edit, rename and delete files, star, fork and delete them; read, write, edit and delete
  comments.
* **Built-in downloader** that works on Wi-Fi, mobile data, roaming and VPN and resumes
  after a lost connection.
* Wide tables and code blocks in READMEs scroll sideways.
* A scroll-to-top button on long lists and an animated logo in the side menu.
* **Help** inside the app that explains every screen and setting, written for people who
  are new to GitHub.
* New app icon, a fixed toolbar and readable dialogs in dark themes.
* Up-to-date libraries throughout (RxJava 3, Retrofit 3, AndroidX, Material 1.14) and no
  more dependencies on JCenter or the old support library.
* Uses the current GitHub REST API version (2026-03-10) for every request.

## Features

### App
* Login with a personal access token or with the browser (OAuth); multiple accounts:
  switch, add or log out from the side menu of any screen
* A saved sign-in is never removed by itself: when GitHub stops accepting it, the app
  asks you to sign in again and keeps the account
* GitHub notifications on the phone (optional): Mark as read, Mark as done, Unsubscribe,
  and why each notification came
* **Releases** of all your starred repositories in one list, newest first, with New
  badges and download buttons; it can be the start page
* Built-in updater with an on/off switch; GitHub links from other apps open in the app
  (repositories, also `.git` links, files, folders, wiki pages, issues, pull requests,
  releases, profiles and their repositories, stars, followers and following); a file link
  to a branch that does not exist says so and offers the default branch
* The side menu on every screen (≡ always at the top left, Back right next to it), to jump
  anywhere without pressing Back many times
* **Matrix rain** side menu: slowly falling green characters behind your account and down
  the edges of the menu, with an animated "Welcome to OpenHub"; or the plain theme color
* Settings, About and Help in the same Matrix look: the rain behind a large decoding title,
  an almost black screen on dark themes; settings in groups with colored icons
* Every other screen gets a black Matrix top bar with the rain and a title that decodes
  letter by letter (My Profile, repository names over up to three lines)
* Modern rounded buttons that sink a little when pressed; readable hints and labels in
  dark themes; a new theme or accent color reaches every open screen
* About shows the SHA-256 of the APK's signing certificate (tap to copy), checks for
  updates and links to private security reports
* **App icon** to choose: Matrix, Classic, Cosmic or Outline
* Searchable in-app help that explains every screen, written for people new to GitHub;
  its feature tiles jump to the matching section
* Light, dark and AMOLED dark themes, many accent colors, colored or plain side menu icons
* Comments look like on github.com (GitHub's own rendering): pictures, tables, task lists,
  alerts with GitHub's icons, quotes, nested lists, footnotes, keys, bold `@mentions` and
  folded sections that open on a tap; a tap on a table opens it full screen
* **Quote reply** and **Mention `@name`** in the long-press menu of every comment (issues,
  pull requests, discussions, gists)
* Code highlighting for 193 languages; a copy button on every code block and code file
  (copies the code without line numbers)
* A select all / clear mark in text fields
* Offline mode (cached pages); Clear cache in the settings, and the cache of web pages is
  kept small by itself
* Web links open in a Custom Tab of your default browser on top of the app, or in the
  browser you pick from a list of all your browsers
* Trending repositories for all languages, by day, week or month
* GitHub collections and featured topics
* Search repositories and users, with a search history (× forgets one search, or clear
  all)
* **Filter** field in My repos, Starred repos and the repository lists of every profile:
  narrows the list while you type (name, description, language, topics; case and accents do
  not matter) and searches the whole list, not only the repositories already loaded
* Bookmarks and a trace of what you viewed (swipe to remove, or clear the trace)
* **Pull down to reload** on every screen that shows GitHub data (repository, profile,
  files, pull requests, discussions, releases, logs and more); lists also update
  themselves when you come back to them
* Launcher shortcuts
* Option to skip loading images on mobile data (a picture you tap still loads; avatars not
  loaded yet show a grey person)

### Repositories
* Create repositories (README, .gitignore templates and your own lines, license);
  repository settings (also pull requests on or off and who may create them), transfer,
  archive and delete
* Branches: browse their files, create, rename, delete, restore, and branch protection
* **Tags**: every tag with its commit, date and release; find by name, browse the files
  of a tag, compare with the tag before it, download its source code, make a release from
  it, delete tags
* **Rulesets** for branches and tags: create, edit, switch on and off, delete, the
  history of a ruleset with restore, bypass for admins, and converting branch protection
  into a ruleset; the rules that apply to a branch
* **Compare** two branches or tags: commits ahead and behind, changed files and diffs, and
  a pull request from there
* Create, upload, edit, rename and delete files as real commits, to the branch you
  choose or a new branch with a pull request; files shared from other apps can be
  committed to a folder of a repository; submodules open the repository they point to
* **History** of a file or folder, and **blame**: who last changed each line and when,
  with age colors
* **Search code** in a repository, with the matches marked; file name search where
  GitHub's code search has nothing
* Repository info, README (with a pencil to edit it), files, commits, activity, branches
  and tags
* Star, watch and fork; **Sync fork** like on github.com: how far a fork branch is ahead of
  and behind the original, update it (fast-forward or merge commit) or discard its own
  commits, help with the workflows GitHub keeps off in a new fork, and **Contribute** a pull
  request to the original
* Releases: create, edit, publish and delete, upload APKs and zips (also by sharing from
  other apps such as Termux), rename and delete files, Latest and Pre-release badges,
  file sizes and download counts; downloads are checked against GitHub's SHA-256
* **SHA256SUMS** file for a release (for `sha256sum -c`), written from GitHub's checksums
* **Clean up old releases**: keep the newest one, 3 or 5 and delete the rest, with their tags
* Release notifications for any repository
* GitHub Actions: workflow runs with their jobs and steps, **job logs** (folded groups,
  errors marked, find, next error, download), re-run (all, failed or one job, with
  debug logging) and cancel, artifacts (download, or unpack and add their files to a
  release in a few taps), starting workflows by hand (the new run opens right away),
  and a notification
  when a run finishes, with buttons to download its artifacts
* **Workflows**: enable and disable them, show their runs, open their files; **caches**:
  storage used, sorted lists, delete one or all
* **Security**: Dependabot, code scanning and secret scanning alerts with details
  (for Dependabot also your version, CVSS 4.0 and 3.x scores, the EPSS exploit chance);
  dismiss, close and reopen them; admins turn Dependabot alerts and security updates,
  secret scanning, push protection and private vulnerability reporting on or off
* **Deploy keys**: add (paste the key or pick a .pub file; private keys are refused on the
  phone) and delete, with SHA256 fingerprints and when each key was last used
* **Webhooks**: how the last delivery went, turn off and on, ping, delete; recent
  deliveries with the receiver's answer and the payload, and redeliver
* **Secrets**: add, update and delete the secrets of Actions, Dependabot and environments;
  values are encrypted on the phone as GitHub requires
* **Actions variables** of the repository and of environments: add, edit, rename, copy and
  delete
* **Discussions**: read, comment and reply, upvote, mark the answer, start discussions,
  vote in polls, close (resolved, outdated, duplicate) and reopen
* Download source code
* Your private, public, forked and starred repositories
* **Wiki** pages rendered in the app, with links between pages, the wiki's contents and
  its recent changes; links in a README to files and wiki pages open in the app, and its
  table of contents jumps to the section

### Pull requests
* List, conversation with reviews and checks, commits and files
* Open pull requests (also from forks), edit, close and reopen, draft and ready
* Merge, squash or rebase with GitHub's asynchronous merge (also into a merge queue,
  with bypass of the rules when you may); delete and restore the branch
* Archive and unarchive pull requests (triage access or higher), with an Archived tab
* **Stacked pull requests**: the stack in the list and on the pull request, with links
  to every pull request in it; merging takes the open ones below along
* A blocked merge lists what the base branch asks for: the rules of its rulesets and
  branch protection, approvals so far, and unresolved conversations
* Line-by-line review of the diff: comment on one or several lines, suggest changes,
  reply, edit and delete your comments
* **Commit suggestions** from a review, one by one or several as one commit, with their
  authors as co-authors
* **Pending reviews**: collect line comments and replies to threads and send them as one
  review (Comment, Approve or Request changes)
* Resolve and unresolve conversations

### Issues and commits
* Issues with comments and events; create, edit, close and reopen; issue lists show only
  issues (pull requests have their own list)
* **Attach pictures and videos** to issues, comments and release notes (repositories you can
  write to); comments show their pictures, quotes have a clear grey bar; videos play in the
  app and keep playing in a small window (picture in picture) when you press Home
* **Sub-issues**: the parent, progress, add an existing or a new issue, reorder, remove
* **Relationships**: blocked by, blocking and relates to
* Issue types and issue fields (organization repositories)
* **Issue templates** (Markdown templates and issue forms) when creating an issue
* Labels, assignees and milestone for people with triage or write access
* **Milestones**: open and closed, due dates and progress, their issues; create, edit,
  close, reopen and delete
* Comments with a Markdown editor and preview; drafts drawn on the phone link URLs,
  `@mentions` and `#123` like github.com
* **Unsent comments are kept**: a new comment on an issue, pull request, discussion or gist
  stays when you leave without sending it, also after closing the app
* Manage labels in your own repositories
* Your created, assigned and mentioned issues
* Commit details, changed files and diffs

### Users and organizations
* Profile, followers, following and organizations
* Public repositories, starred repositories, gists and activity
* Follow and unfollow
* Organization repositories, activity and members

## Security and privacy

OpenHub talks only to GitHub: no ads, no analytics, no servers of its own.

* **Your token stays on the phone, encrypted.** It is stored with AES-256-GCM using a key
  of the Android Keystore, which never leaves the phone. A copied database is useless
  without that key.
* **The token goes only to `api.github.com`.** File links, images and web pages never get
  it, whatever address they point to; each account's token is bound to its own HTTP client.
* **Not in backups.** Accounts and tokens are excluded from Android backups and from
  phone-to-phone transfers; settings are kept.
* **Short-lived browser sign-in.** A browser sign-in token lasts 8 hours and renews itself
  with a refresh token, which is encrypted the same way; if GitHub refuses the renewal,
  the app asks you to sign in again (one notification when it is in the background).
* **Browser sign-in** uses a one-time `state` that must come back unchanged and PKCE
  (S256), so another app cannot push its own sign-in into OpenHub; the client secret and
  the code travel in the request body, never in a URL.
* **Log out** deletes the token and the cached GitHub pages; a browser sign-in token is
  also revoked at GitHub. Personal access tokens are revoked by you on github.com.
* **Least privilege:** *Create token* ticks every permission OpenHub can use; untick
  `delete_repo` or `workflow` if you do not need them, or use a fine-grained token limited
  to some repositories.
* **Encrypted connections only.** No plain HTTP; only the system's certificate
  authorities are trusted (user-installed certificates are not), and the token is never
  sent over a connection that is not HTTPS.
* Web views cannot read files or other apps' content; exported screens accept only what
  they need. Files shared to OpenHub must come from other apps (`content://`), so no app
  can make OpenHub upload its own private files.

Found a security problem? Please report it privately, see [SECURITY.md](SECURITY.md).

## Requirements

Android 8.0 (API 26) or newer.

## Download

APKs are on the [Releases](https://github.com/nikakvo/OpenHub/releases) page. The app id is
`io.github.nikakvo.openhub`, so it installs next to the original OpenHub.

Official APKs are signed with this certificate (SHA-256):

```
5e:99:44:a9:e7:ed:01:1d:83:5b:9a:6f:10:ce:e1:49:6d:88:26:f3:e5:7d:9a:b5:38:6d:ef:d6:d4:d9:98:7e
```

Check a downloaded APK with `apksigner verify --print-certs OpenHub-<version>.apk`
(Android SDK build-tools): the "SHA-256 digest" must be the same, without the colons.

## Building

You need JDK 17 and the Android SDK (platform 37). Then:

```bash
./gradlew assembleNormalDebug
```

Unit tests: `./gradlew testNormalDebugUnitTest`. GitHub Actions runs them and a debug build on every push and pull request (see the Tests badge above).

The APK ends up in `app/build/outputs/apk/normal/debug/`. There are two flavors that only
differ in the app name: `normal` ("OpenHub") and `fullName` ("OpenHub for GitHub").

### Browser login (optional)

Without an OAuth App the app offers token login only. To enable "Login with browser",
create an OAuth App at <https://github.com/settings/developers> with the callback URL
`openhub://login` and put its keys in `local.properties` (not tracked by git):

```properties
openhub_client_id=your_client_id
openhub_client_secret=your_client_secret
```

### Signed release builds (optional)

The APKs on the Releases page are signed with the project's own key, and Android only
installs an update that carries the same signature. Your own release builds are signed
with the debug key unless a keystore is configured, so they always install (as a
different app signature: uninstall the official APK first). To sign with your own key, either add `sign_path`, `store_password`,
`key_alias` and `key_password` to `local.properties`, or use `build.sh`:

```bash
KEYSTORE=~/keys/my.jks KEY_ALIAS=mykey OUT_DIR=~/apk ./build.sh
```

`build.sh` reads the passwords from a file next to the keystore with the same name and the
`.env` extension (`KEYSTORE_PASSWORD=...` and `KEY_PASSWORD=...`, `chmod 600`) or asks for
them, passes them to Gradle as environment variables, builds the `normal` release and
copies the APK to `OUT_DIR` as `OpenHub-<version>.apk` (test versions with a `-`
suffix also get a timestamp). Use
`FLAVOR=FullName` for the other flavor.

Never commit keys or passwords: `public.properties` is in git and must stay empty.

## Credits

* [**ThirtyDegreesRay**](https://github.com/ThirtyDegreesRay/OpenHub) created OpenHub.
* [**yxj-github-437**](https://github.com/yxj-github-437/OpenHub) kept it alive and updated;
  this fork starts from that work.
* [**Kosh**](https://github.com/k0shk0sh/FastHub): some code comes from FastHub.

## Libraries

* [Room](https://developer.android.com/training/data-storage/room) The SQLite object mapping library of Android Jetpack.
* [RxJava](https://github.com/ReactiveX/RxJava) A library for composing asynchronous and event-based programs using observable sequences for the Java VM.
* [RxAndroid](https://github.com/ReactiveX/RxAndroid) RxJava bindings for Android.
* [Retrofit](https://github.com/square/retrofit) Type-safe HTTP client for Android and Java by Square, Inc.
* [Dagger](https://github.com/google/dagger) A fast dependency injector for Android and Java.
* [Glide](https://github.com/bumptech/glide) An image loading and caching library for Android focused on smooth scrolling.
* [DataAutoAccess](https://github.com/ThirtyDegreesRay/DataAutoAccess) A simple way to access android bundle data.
* [GitHub-Trending](https://github.com/thedillonb/GitHub-Trending) Scrapes GitHub's Trending Pages.
* [commonmark-java](https://github.com/commonmark/commonmark-java) Markdown on the phone, for drafts and when GitHub cannot be reached.
* [Media3 ExoPlayer](https://github.com/androidx/media) The video player for attached videos.
* [jsoup](https://github.com/jhy/jsoup) Java HTML Parser, with best of DOM, CSS, and jquery.
* [OctoDroid](https://github.com/slapperwan/gh4a) This application provides access to GitHub and lets you stay connected with your network.
* [FastHub](https://github.com/k0shk0sh/FastHub) FastHub the ultimate GitHub client for Android.
* [highlight.js](https://github.com/highlightjs/highlight.js) Syntax highlighting of code files.
* [github-markdown-css](https://github.com/sindresorhus/github-markdown-css) GitHub's Markdown style for READMEs, wiki pages and comments opened alone.
* [highlightjs-line-numbers.js](https://github.com/yauhenipakala/highlightjs-line-numbers.js) Display code line number with highlightjs.

## Contributors to the original OpenHub

*Order by commit time.* The translations were removed in this fork (it is English only), but
the work that went into them is still credited here.

* [moriel5](https://github.com/moriel5) - Hebrew translation
* [Henry2o1o](https://github.com/Henry2o1o), [squeezer11](https://github.com/squeezer11), [ropaha](https://github.com/ropaha) - German translation
* [Edfgdz](https://github.com/Edfgdz) - Testing and suggest
* [pzhlkj6612](https://github.com/pzhlkj6612) - Improve the translation of Chinese readme
* [hiteshnayak305](https://github.com/hiteshnayak305) - Hindi translation
* [KcNirvana](https://github.com/KcNirvana) - Portuguese translation
* [sclickk](https://github.com/sclickk) - Improve English translation
* [Zazsona](https://github.com/Zazsona) - Add AMOLED theme
* [Aqueminivan](https://github.com/Aqueminivan) - Improve the translation of English readme
* [XF-zhjnc](https://github.com/XF-zhjnc) - Fix bugs
* [fergthh](https://github.com/fergthh) - Spanish translation
* [chiyi4488](https://github.com/chiyi4488) - Traditional Chinese translation
* [left4j](https://github.com/left4j) - Russian translation
* [TheParanoik](https://github.com/TheParanoik) - Polish translation
* [X1nto](https://github.com/X1nto) - Georgian translation
* [MerkeX](https://github.com/MerkeX) - Italian translation
* [Vistaus](https://github.com/Vistaus) - Dutch translation
* [pkubanek](https://github.com/pkubanek) - Czech translation
* [LouisUnnoficial](https://github.com/LouisUnnoficial) - Portuguese(Brazil) translation


## License

GPL-3.0, like the original. See [LICENSE](LICENSE).

ThirtyDegreesRay:
> Copyright (C) 2017 ThirtyDegreesRay.
> Licensed under the [GPL-3.0](https://www.gnu.org/licenses/gpl.html).
> (See the [LICENSE](https://github.com/ThirtyDegreesRay/OpenHub/blob/master/LICENSE) file for the whole license text.)

Changes in this fork:
> Copyright (C) 2026 Tears Burn (nikakvo).
> Licensed under the [GPL-3.0](https://www.gnu.org/licenses/gpl.html).

Kosh (13 files contain code copied from FastHub):
> Copyright (C) 2017 Kosh.
> Licensed under the [GPL-3.0](https://www.gnu.org/licenses/gpl.html) license.
> (See the [LICENSE](https://github.com/k0shk0sh/FastHub/blob/master/LICENSE) file for the whole license text.)
