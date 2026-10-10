package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import android.widget.TextView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.ui.activity.MarkdownEditorCallback;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.StringUtils;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import android.view.View;


/**
 * Created by ThirtyDegreesRay on 2017/9/29 11:52:42
 */
public class MarkdownPreviewFragment extends BaseFragment{

    public static MarkdownPreviewFragment create(){
        MarkdownPreviewFragment fragment = new MarkdownPreviewFragment();
        return fragment;
    }

    TextView previewText;
    private Markwon markwon;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_markdown_preview;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {

    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        previewText.setText(R.string.nothing_to_preview);
    }

    private Markwon getMarkwon(){
        if(markwon == null){
            // GitHub flavored: tables, ~~strikethrough~~, - [ ] task lists, bare links.
            markwon = Markwon.builder(requireContext())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(requireContext()))
                    .usePlugin(TaskListPlugin.create(requireContext()))
                    .usePlugin(LinkifyPlugin.create())
                    .build();
        }
        return markwon;
    }

    private MarkdownEditorCallback getMarkdownEditorCallback(){
        return (MarkdownEditorCallback) getActivity();
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(getMarkdownEditorCallback().isTextChanged()){
            if(StringUtils.isBlank(getMarkdownEditorCallback().getText())){
                previewText.setText(R.string.nothing_to_preview);
            }else{
                getMarkwon().setMarkdown(previewText, getMarkdownEditorCallback().getText());
            }
        }
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        previewText = root.findViewById(R.id.preview_text);
    }
}
