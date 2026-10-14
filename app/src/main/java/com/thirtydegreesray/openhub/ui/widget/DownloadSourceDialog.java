package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.DownloadSource;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;
import com.thirtydegreesray.openhub.ui.adapter.DownloadSourcesAdapter;

import java.util.ArrayList;

/**
 * Created by ThirtyDegreesRay on 2017/9/16 23:21:22
 */

public class DownloadSourceDialog {

    public static void show(Context context, String repoName, String tagName, Release release){
        show(context, repoName, tagName, getDownloadSources(context, release));
    }

    /** Release files plus the source code archives of a tag (the release feed has no Release). */
    public static void show(Context context, String fullName, String tagName,
                            java.util.List<DownloadSource> assets){
        ArrayList<DownloadSource> sources = new ArrayList<>(assets);
        String base = "https://github.com/" + fullName + "/archive/refs/tags/" + tagName;
        sources.add(new DownloadSource(base + ".zip", true, context.getString(R.string.source_code_zip)));
        sources.add(new DownloadSource(base + ".tar.gz", true, context.getString(R.string.source_code_tar)));
        String repoName = fullName.substring(fullName.indexOf('/') + 1);
        show(context, repoName, tagName, sources);
    }

    private static void show(Context context, String repoName, String tagName,
                             ArrayList<DownloadSource> sources){
        DownloadSourcesAdapter adapter = new DownloadSourcesAdapter(context, repoName, tagName);
        adapter.setData(sources);

        final RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        new AlertDialog.Builder(context)
                .setCancelable(true)
                .setTitle(R.string.download)
                .setView(recyclerView)
                .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    private static ArrayList<DownloadSource> getDownloadSources(Context context, Release release){
        ArrayList<DownloadSource> sources = new ArrayList<>();
        if (release.getAssets() != null) {
            for(ReleaseAsset asset : release.getAssets()){
                sources.add(new DownloadSource(asset.getDownloadUrl(), asset.getName(), asset.getSize(),
                        asset.getDownloadCount()).setDigest(asset.getDigest()));
            }
        }
        // a draft has no tag yet, so there is no source archive
        if (release.getZipballUrl() != null) {
            sources.add(new DownloadSource(release.getZipballUrlWrap(), true,
                    context.getString(R.string.source_code_zip)));
        }
        if (release.getTarballUrl() != null) {
            sources.add(new DownloadSource(release.getTarballUrlWrap(), true,
                    context.getString(R.string.source_code_tar)));
        }
        return sources;
    }

}
