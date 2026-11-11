

package com.thirtydegreesray.openhub.ui.activity;

/**
 * Created by ThirtyDegreesRay on 2017/10/21 12:59:52
 */

public interface MarkdownEditorCallback {
    String getText();

    boolean isTextChanged();

    /** "owner/repo" for #123 and @user links in the Preview, or null. */
    @androidx.annotation.Nullable
    default String getRepoFullName() {
        return null;
    }
}
