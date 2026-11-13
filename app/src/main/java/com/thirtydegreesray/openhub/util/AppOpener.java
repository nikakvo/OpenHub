package com.thirtydegreesray.openhub.util;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import android.os.Build;
import android.widget.Toast;
import android.Manifest;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.service.DownloadService;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.thirtydegreesray.openhub.mvp.model.GitHubName;
import com.thirtydegreesray.openhub.service.CopyBroadcastReceiver;
import com.thirtydegreesray.openhub.service.ShareBroadcastReceiver;
import com.thirtydegreesray.openhub.ui.activity.CommitDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.CommitsListActivity;
import com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.IssuesActivity;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.activity.RepoListActivity;
import com.thirtydegreesray.openhub.ui.activity.UserListActivity;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;
import com.thirtydegreesray.openhub.ui.fragment.UserListFragment;
import com.thirtydegreesray.openhub.ui.activity.ReleaseInfoActivity;
import com.thirtydegreesray.openhub.ui.activity.ReleasesActivity;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Created by ThirtyDegreesRay on 2017/10/30 11:18:57
 */

public class AppOpener {

    public static void openInCustomTabsOrBrowser(@NonNull Context context, @NonNull String url){
        if(StringUtils.isBlank(url)){
            Toasts.warning(context, context.getString(R.string.invalid_url), Toast.LENGTH_LONG).show();
            return;
        }
        //check http prefix
        if(!url.contains("//")){
            url = "http://".concat(url);
        }

        String customTabsPackageName ;
        if (PrefUtils.isCustomTabsEnable() &&
                (customTabsPackageName = CustomTabsHelper.INSTANCE.getBestPackageName(context) ) != null) {
            Bitmap backIconBitmap = ViewUtils.getBitmapFromResource(context, R.drawable.ic_arrow_back_title);
            Intent shareIntent = new Intent(context.getApplicationContext(), ShareBroadcastReceiver.class);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent sharePendingIntent = PendingIntent.getBroadcast(
                    context.getApplicationContext(), 0, shareIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
            Intent copyIntent = new Intent(context.getApplicationContext(), CopyBroadcastReceiver.class);
            copyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent copyPendingIntent = PendingIntent.getBroadcast(
                    context.getApplicationContext(), 0, copyIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);

            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(ViewUtils.getPrimaryColor(context)).build())
                    .setCloseButtonIcon(backIconBitmap)
                    .setShowTitle(true)
                    .addMenuItem(context.getString(R.string.share), sharePendingIntent)
                    .addMenuItem(context.getString(R.string.copy_url), copyPendingIntent)
//                    .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
//                    .setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                    .build();
            customTabsIntent.intent.setPackage(customTabsPackageName);
            try {
                customTabsIntent.launchUrl(context, Uri.parse(url));
            } catch (ActivityNotFoundException | SecurityException e) {
                // the browser went away or refuses the link: open it the normal way
                openInBrowser(context, url);
                return;
            }

            if(PrefUtils.isCustomTabsTipsEnable()){
                Toasts.info(context, context.getString(R.string.use_custom_tabs_tips), Toast.LENGTH_LONG).show();
                PrefUtils.set(PrefUtils.CUSTOM_TABS_TIPS_ENABLE, false);
            }

        } else {
            openInBrowser(context,url);
        }

    }

    public static void openInBrowser(@NonNull Context context, @NonNull String url){
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent chooser = createActivityChooserIntent(context, intent, uri, VIEW_IGNORE_PACKAGE);
        if (chooser == SHOWN_OWN_PICKER) return;
        if ((chooser != null && start(context, chooser)) || openWithDefaultApp(context, uri)) return;
        Toasts.warning(context, context.getString(R.string.no_browser_clients), Toast.LENGTH_LONG).show();
    }

    /**
     * Last try before "no app": a plain link intent, so Android starts the default browser
     * itself (apps that hide from our list still get it). Never OpenHub or the system
     * chooser (that would offer OpenHub again).
     */
    private static boolean openWithDefaultApp(@NonNull Context context, @NonNull Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo target = context.getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (target == null || target.activityInfo == null || !BrowserTargets.isDirectTarget(
                target.activityInfo.packageName, target.activityInfo.name, context.getPackageName())) {
            return false;
        }
        view.setPackage(target.activityInfo.packageName);
        return start(context, view);
    }

    private static boolean start(@NonNull Context context, @NonNull Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /**
     * Starts a download with the built-in downloader (any network: Wi-Fi, mobile, roaming,
     * VPN), or hands the link to another app when the built-in downloader is turned off.
     */
    public static void startDownload(@NonNull Context context, @NonNull String url, String fileName) {
        startDownload(context, url, fileName, null);
    }

    /** @param digest GitHub's checksum of the file ("sha256:..."): checked after the download */
    public static void startDownload(@NonNull Context context, @NonNull String url, String fileName,
                                     @Nullable String digest) {
        if(!PrefUtils.isSystemDownloader()){
            openDownloader(context, url);
            return;
        }
        if(StringUtils.isBlank(url) || StringUtils.isBlank(fileName)){
            Toasts.error(context, context.getString(R.string.download_empty_tip)).show();
            return;
        }
        final Context appContext = context.getApplicationContext();
        BaseActivity activity = BaseActivity.getCurActivity();
        // Android 8-9 only: the public Download folder needs the storage permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(appContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (activity != null) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
            Toasts.warning(appContext, appContext.getString(R.string.permission_storage_denied),
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Ask once for notifications (progress), but never block the download on it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null
                && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
        DownloadService.start(appContext, url, fileName, digest);
    }

    public static void openDownloader(@NonNull Context context, @NonNull String url) {
        if(StringUtils.isBlank(url)){
            Toasts.warning(context, context.getString(R.string.invalid_url), Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent chooser = createActivityChooserIntent(context, intent, uri, VIEW_IGNORE_PACKAGE);
        if (chooser == SHOWN_OWN_PICKER) return;
        if ((chooser != null && start(context, chooser)) || openWithDefaultApp(context, uri)) return;
        Toasts.warning(context, context.getString(R.string.no_download_clients), Toast.LENGTH_LONG).show();
    }

    public static void shareText(@NonNull Context context, @NonNull String text) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.setType("text/plain");
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_to))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch (ActivityNotFoundException e){
            Toasts.warning(context, context.getString(R.string.no_share_clients)).show();
        }
    }

    public static void launchEmail(@NonNull Context context, @NonNull String email){
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_email))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch (ActivityNotFoundException e){
            Toasts.warning(context, context.getString(R.string.no_email_clients)).show();
        }
    }

    public static void openInMarket(@NonNull Context context){
        Uri uri = Uri.parse("market://details?id=" + context.getPackageName());
        Intent intent = new Intent(Intent.ACTION_VIEW,uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_in_market))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch (ActivityNotFoundException e){
            Toasts.warning(context, context.getString(R.string.no_market_clients)).show();
        }
    }

    public static void launchUrl(@NonNull Context context, @NonNull Uri uri){
        if(StringUtils.isBlank(uri.toString())) return;
        // a video attached on GitHub (github.com/user-attachments/assets/<uuid>): the player
        if (Attachments.isVideoLink(uri.toString())) {
            com.thirtydegreesray.openhub.ui.activity.VideoPlayerActivity.show(context, uri.toString());
            return;
        }
        // github.com/user?tab=repositories (or stars, followers, following): that list
        String profileTab = GitHubLinks.profileTab(uri.toString());
        // "github.com/owner/repo.git", "...repo#readme" and the like open the repository
        String url = GitHubLinks.normalize(uri.toString());
        uri = Uri.parse(url);
        // github.com/sponsors/user, /orgs/..., /topics/...: GitHub's own pages, not a repository
        if (GitHubLinks.isGitHubPage(url)) {
            openInCustomTabsOrBrowser(context, url);
            return;
        }
        GitHubLinks.Blob blob = GitHubLinks.blob(url);
        if(GitHubHelper.isImage(url)){
            // an image in a repository: its file, not the github.com page around it
            ViewerActivity.showImage(context, blob != null ? blob.rawUrl() : url);
            return;
        }
        // files of a repository (README links like docs/INSTALL.md) open in the file viewer
        if (blob != null) {
            com.thirtydegreesray.openhub.mvp.model.FileModel file =
                    new com.thirtydegreesray.openhub.mvp.model.FileModel();
            file.setName(blob.name());
            file.setPath(blob.path);
            file.setUrl(blob.apiUrl());
            file.setHtmlUrl(blob.htmlUrl());
            file.setDownloadUrl(blob.rawUrl());
            ViewerActivity.show(context, file, blob.repo);
            return;
        }
        // wiki pages (github.com/owner/repo/wiki/Page)
        GitHubLinks.Wiki wiki = GitHubLinks.wiki(url);
        if (wiki != null) {
            context.startActivity(com.thirtydegreesray.openhub.ui.activity.WikiPageActivity
                    .createIntent(context, wiki.owner, wiki.repo, wiki.page, true));
            return;
        }
        // a folder at a branch (github.com/owner/repo/tree/dev/docs): the Files tab there
        GitHubLinks.Tree tree = GitHubLinks.tree(url);
        if (tree != null) {
            Intent treeIntent = RepositoryActivity.createFilesIntent(context, tree.owner, tree.repo,
                    tree.ref, tree.path);
            if (!(context instanceof android.app.Activity)) treeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(treeIntent);
            return;
        }
        // pull requests open in the app (github.com/o/r/pull/12 and API addresses)
        Intent pullIntent = com.thirtydegreesray.openhub.ui.activity.PullRequestActivity.createIntent(context, url);
        if (pullIntent != null) {
            context.startActivity(pullIntent);
            return;
        }
        // workflow runs and their jobs
        ActionsLinks runLink = ActionsLinks.parse(url);
        if (runLink != null) {
            Intent runIntent = runLink.jobId != 0
                    ? com.thirtydegreesray.openhub.ui.activity.JobLogActivity.createIntent(context,
                            runLink.owner, runLink.repo, runLink.jobId, null, url)
                    : com.thirtydegreesray.openhub.ui.activity.WorkflowRunActivity.createIntent(context,
                            runLink.owner, runLink.repo, runLink.runId);
            if (!(context instanceof android.app.Activity)) runIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(runIntent);
            return;
        }
        // the Actions tab (github.com/owner/repo/actions)
        String[] actionsTab = ActionsLinks.tab(url);
        if (actionsTab != null) {
            Intent tabIntent = com.thirtydegreesray.openhub.ui.activity.ActionsActivity
                    .createLinkIntent(context, actionsTab[0], actionsTab[1]);
            if (!(context instanceof android.app.Activity)) tabIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(tabIntent);
            return;
        }
        // gists (gist.github.com/user/id)
        String gistId = GistLinks.id(url);
        if (gistId != null) {
            Intent gistIntent = com.thirtydegreesray.openhub.ui.activity.GistActivity.createIntent(context, gistId);
            if (!(context instanceof android.app.Activity)) gistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(gistIntent);
            return;
        }
        // discussions too (github.com/o/r/discussions and /discussions/12)
        Intent discussionIntent = com.thirtydegreesray.openhub.ui.activity.DiscussionActivity.createIntent(context, url);
        if (discussionIntent != null) {
            if (!(context instanceof android.app.Activity)) discussionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(discussionIntent);
            return;
        }
        GitHubName gitHubName = GitHubName.fromUrl(url);
        String userName ;
        String repoName ;
        if(gitHubName == null){
            openInCustomTabsOrBrowser(context, uri.toString());
            return;
        } else {
            userName = gitHubName.getUserName();
            repoName = gitHubName.getRepoName();
        }

        if(GitHubHelper.isUserUrl(url) && profileTab != null && openProfileTab(context, userName, profileTab)){
            return;
        } else if(GitHubHelper.isUserUrl(url)){
            ProfileActivity.show((Activity) context, userName);
        } else if(GitHubHelper.isRepoUrl(url)){
            RepositoryActivity.show(context, userName, repoName);
        } else if (GitHubHelper.isIssueUrl(url)) {
            IssuesActivity.showForRepo((Activity) context, userName, repoName);
        } else if (GitHubHelper.isIssueDetailUrl(url)) {
            IssueDetailActivity.show((Activity) context, url);
        } else if (GitHubHelper.isReleasesUrl(url)) {
            ReleasesActivity.show((Activity) context, userName, repoName);
        } else if (GitHubHelper.isReleaseTagUrl(url)) {
            ReleaseInfoActivity.show((Activity) context, userName, repoName, gitHubName.getReleaseTagName());
        } else if (GitHubHelper.isCommitUrl(url)) {
            CommitDetailActivity.show((Activity) context, url);
        } else if (GitHubHelper.isCompareUrl(url)) {
            String[] compare = gitHubName.getCompareDetails();
            CommitsListActivity.showForCompare((Activity) context, userName, repoName, compare[0], compare[1]);
        } else {
            openInCustomTabsOrBrowser(context, url);
        }
    }

    /** The list a profile tab shows; false for tabs the app has no list for (the profile opens). */
    private static boolean openProfileTab(@NonNull Context context, @NonNull String user, @NonNull String tab) {
        com.thirtydegreesray.openhub.mvp.model.User me = com.thirtydegreesray.openhub.AppData.INSTANCE.getLoggedUser();
        boolean own = me != null && user.equalsIgnoreCase(me.getLogin());
        switch (tab) {
            case "repositories":
                RepoListActivity.show(context, own ? RepositoriesFragment.RepositoriesType.OWNED
                        : RepositoriesFragment.RepositoriesType.PUBLIC, user);
                return true;
            case "stars":
                RepoListActivity.show(context, RepositoriesFragment.RepositoriesType.STARRED, user);
                return true;
            case "followers":
                if (!(context instanceof Activity)) return false;
                UserListActivity.show((Activity) context, UserListFragment.UserListType.FOLLOWERS, user);
                return true;
            case "following":
                if (!(context instanceof Activity)) return false;
                UserListActivity.show((Activity) context, UserListFragment.UserListType.FOLLOWING, user);
                return true;
            default:
                return false;
        }
    }

    public static boolean isAppAlive(Context context, String packageName){
        ActivityManager activityManager =
                (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processInfos
                = activityManager.getRunningAppProcesses();
        for(int i = 0; i < processInfos.size(); i++){
            if(processInfos.get(i).processName.equals(packageName)){
                return true;
            }
        }
        return false;
    }


    @Nullable
    private static Intent createActivityChooserIntent(Context context, Intent intent,
                                                      Uri uri, List<String> ignorPackageList) {
        final PackageManager pm = context.getPackageManager();
        final String ourPackageName = context.getPackageName();
        // MATCH_ALL alone: with MATCH_DEFAULT_ONLY Android 12+ answers a github.com link with
        // the apps that may open github.com links (OpenHub, other GitHub clients) and hides
        // every browser
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        List<ResolveInfo> targets = targets(activities, ourPackageName, ignorPackageList);
        if (targets.isEmpty() && uri != null
                && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            // nothing left (only OpenHub or other GitHub clients): ask which apps open any
            // web page, then give them this link
            Intent anyPage = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            targets = targets(pm.queryIntentActivities(anyPage, PackageManager.MATCH_ALL),
                    ourPackageName, ignorPackageList);
        }
        if (targets.isEmpty()) return null;
        Collections.sort(targets, new ResolveInfo.DisplayNameComparator(pm));

        final ArrayList<Intent> chooserIntents = new ArrayList<>();
        for (ResolveInfo resInfo : targets) {
            ActivityInfo info = resInfo.activityInfo;
            Intent targetIntent = new Intent(intent);
            // the exact activity Android found (works for filters without DEFAULT too)
            targetIntent.setClassName(info.packageName, info.name);
            targetIntent.setDataAndType(uri, intent.getType());
            chooserIntents.add(targetIntent);
        }
        if (chooserIntents.size() == 1) {
            // there was only one, no need to show the chooser
            return chooserIntents.get(0);
        }

        // Android's chooser shows only 2 extra apps (EXTRA_INITIAL_INTENTS, Android 10+):
        // with more browsers some would be missing, so the app shows its own list
        Activity activity = context instanceof Activity ? (Activity) context : BaseActivity.getCurActivity();
        if (activity != null && !activity.isFinishing()) {
            showAppPicker(activity, targets, chooserIntents);
            return SHOWN_OWN_PICKER;
        }
        final Intent lastIntent = chooserIntents.remove(chooserIntents.size() - 1);
        Intent chooserIntent = Intent.createChooser(lastIntent, null);
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                chooserIntents.toArray(new Intent[chooserIntents.size()]));
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    /** marker: {@link #createActivityChooserIntent} showed the app's own list, nothing to start */
    private static final Intent SHOWN_OWN_PICKER = new Intent();

    /** Every app that can open the link, with its icon and name; a tap opens the link in it. */
    private static void showAppPicker(@NonNull Activity activity, @NonNull List<ResolveInfo> targets,
                                      @NonNull List<Intent> intents) {
        final PackageManager pm = activity.getPackageManager();
        final int iconSize = (int) (32 * activity.getResources().getDisplayMetrics().density);
        final int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
        // rows inflated with the dialog's own theme and its text color, so the names are
        // readable in dark themes too
        android.content.res.TypedArray colors = builder.getContext()
                .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        final android.content.res.ColorStateList textColor = colors.getColorStateList(0);
        colors.recycle();
        android.widget.ArrayAdapter<ResolveInfo> adapter = new android.widget.ArrayAdapter<ResolveInfo>(
                builder.getContext(), android.R.layout.select_dialog_item, android.R.id.text1, targets) {
            @NonNull
            @Override
            public android.view.View getView(int position, android.view.View convertView,
                                             @NonNull android.view.ViewGroup parent) {
                android.view.View row = super.getView(position, convertView, parent);
                android.widget.TextView text = row.findViewById(android.R.id.text1);
                ResolveInfo info = getItem(position);
                text.setText(info.loadLabel(pm));
                if (textColor != null) text.setTextColor(textColor);
                android.graphics.drawable.Drawable icon = info.loadIcon(pm);
                icon.setBounds(0, 0, iconSize, iconSize);
                text.setCompoundDrawablesRelative(icon, null, null, null);
                text.setCompoundDrawablePadding(pad);
                return row;
            }
        };
        builder.setTitle(R.string.open_with)
                .setAdapter(adapter, (dialog, which) -> {
                    if (!start(activity, intents.get(which))) {
                        Toasts.warning(activity, activity.getString(R.string.no_browser_clients),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The answers of Android that may be offered ({@link BrowserTargets#pick}). */
    @NonNull
    private static List<ResolveInfo> targets(@NonNull List<ResolveInfo> activities, @NonNull String ourPackage,
                                             @Nullable List<String> ignoredPackages) {
        List<BrowserTargets.Candidate> candidates = new ArrayList<>();
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) continue;
            candidates.add(new BrowserTargets.Candidate(info.activityInfo.packageName,
                    info.activityInfo.name, info.activityInfo.exported && info.activityInfo.enabled));
        }
        List<ResolveInfo> out = new ArrayList<>();
        for (BrowserTargets.Candidate c : BrowserTargets.pick(candidates, ourPackage, ignoredPackages)) {
            for (ResolveInfo info : activities) {
                if (info.activityInfo != null && c.packageName.equals(info.activityInfo.packageName)
                        && c.activityName.equals(info.activityInfo.name)) {
                    out.add(info);
                    break;
                }
            }
        }
        return out;
    }

    private static final List<String> VIEW_IGNORE_PACKAGE = Arrays.asList(
            "com.gh4a", "com.fastaccess", "com.taobao.taobao"
    );

}
