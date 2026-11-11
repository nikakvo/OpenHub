package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.ui.activity.MarkdownEditorCallback;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.ui.widget.AttachFlow;
import com.thirtydegreesray.openhub.ui.widget.ToastAbleImageButton;
import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import com.thirtydegreesray.openhub.util.ViewUtils;


/**
 * Created by ThirtyDegreesRay on 2017/9/29 11:52:17
 */

public class MarkdownEditorFragment extends BaseFragment
        implements MarkdownEditorCallback {

    public static MarkdownEditorFragment create(@Nullable String text, ArrayList<String> mentionUsers) {
        MarkdownEditorFragment fragment = new MarkdownEditorFragment();
        fragment.setArguments(BundleHelper.builder().put("text", text).put("mentionUsers", mentionUsers).build());
        return fragment;
    }

    EditText markdownEdit;
    ToastAbleImageButton addMention;
    @AutoAccess boolean isTextChanged = false;
    @AutoAccess String text;
    @AutoAccess ArrayList<String> mentionUsers;

    private PopupMenu popupMenu;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_markdown_editor;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {

    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        if (!StringUtils.isBlank(text)) {
            isTextChanged = true;
            markdownEdit.setText(text);
            markdownEdit.setSelection(text.length());
        }
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        isTextChanged = true;
    }

    public void afterTextChanged(Editable s){
        int cursorIndex = markdownEdit.getSelectionStart();
        String curContent = markdownEdit.getText().toString();
        String preStr = curContent.substring(0, cursorIndex);
        if(preStr.endsWith("@")){
            showMentionView();
        }else{
            if(popupMenu != null) popupMenu.dismiss();
        }
    }

    @Override
    public String getText() {
        return markdownEdit == null ? null : markdownEdit.getText().toString();
    }

    @Override
    public boolean isTextChanged() {
        isTextChanged = !isTextChanged;
        return !isTextChanged;
    }

    public void onViewClicked(View view) {
        int viewId = view.getId();
        if (viewId == R.id.add_large_head) {
            addKeyWord("#");
        } else if (viewId == R.id.add_medium_head) {
            addKeyWord("##");
        } else if (viewId == R.id.add_small_head) {
            addKeyWord("###");
        } else if (viewId == R.id.add_bold) {
            addKeyWord("****", 2);
        } else if (viewId == R.id.add_italic) {
            addKeyWord("__", 1);
        } else if (viewId == R.id.add_quote) {
            addKeyWord(">");
        } else if (viewId == R.id.insert_code) {
            addKeyWord("``", 1);
        } else if (viewId == R.id.add_link) {
            addKeyWord("[](url)", 1);
        } else if (viewId == R.id.add_bulleted_list) {
            addKeyWord("-");
        } else if (viewId == R.id.add_mention) {
            addKeyWord("@", 1);
            afterTextChanged(markdownEdit.getText());
        }
    }

    private void addKeyWord(String keyWord){
        addKeyWord(keyWord, -1);
    }

    private void addKeyWord(String keyWord, int cursorPosition){
        addKeyWord(keyWord, cursorPosition, true);
    }

    private void addKeyWord(String keyWord, int cursorPosition, boolean preBlankEnable){
        int cursorIndex = markdownEdit.getSelectionStart();
        String curContent = markdownEdit.getText().toString();
        String preStr = curContent.substring(0, cursorIndex);
        String sufStr = curContent.substring(cursorIndex);
        boolean needPreBlank = preBlankEnable
                &&!StringUtils.isBlank(preStr)
                && preStr.charAt(preStr.length() - 1) != ' '
                && preStr.charAt(preStr.length() - 1) != '\n';
        String newStr = preStr;
        if(needPreBlank) {
            newStr = newStr.concat(" ");
        }
        newStr = newStr.concat(keyWord).concat(" ").concat(sufStr);
        int newCursorIndex = cursorIndex + (needPreBlank ? 1 : 0)
                + (cursorPosition == -1 ? keyWord.length() + 1 : cursorPosition);
        markdownEdit.setText(newStr);
        markdownEdit.setSelection(newCursorIndex);
    }

    private void showMentionView(){
        if(mentionUsers != null && mentionUsers.size() > 0){
            if(popupMenu == null){
                popupMenu = new PopupMenu(getActivity(), addMention, Gravity.BOTTOM,
                        R.attr.popup_menu_theme, 0);
                Menu menu = popupMenu.getMenu();
                for(String loginId : mentionUsers){
                    menu.add("@" + loginId);
                }
                popupMenu.setOnMenuItemClickListener(item -> {
                    String loginId = item.getTitle().toString().substring(1);
                    addKeyWord(loginId, -1, false);
                    return false;
                });
            }
            popupMenu.show();
        }
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        markdownEdit = root.findViewById(R.id.markdown_edit);
        addMention = root.findViewById(R.id.add_mention);
        markdownEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                MarkdownEditorFragment.this.onTextChanged(s, start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {
                MarkdownEditorFragment.this.afterTextChanged(s);
            }
        });
        ViewUtils.setOnClick(root, R.id.add_large_head, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_medium_head, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_small_head, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_bold, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_italic, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_quote, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.insert_code, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_link, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_bulleted_list, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.add_mention, v -> onViewClicked(v));
        // "Attach image or video" only when the screen knows the repository
        View attach = root.findViewById(R.id.add_attachment);
        boolean canAttach = getActivity() instanceof AttachFlow.Owner
                && ((AttachFlow.Owner) getActivity()).canAttach();
        attach.setVisibility(canAttach ? View.VISIBLE : View.GONE);
        ViewUtils.setOnClick(root, R.id.add_attachment, v -> {
            if (getActivity() instanceof AttachFlow.Owner) ((AttachFlow.Owner) getActivity()).attach();
        });
    }

    /** Puts the Markdown of an attached file at the cursor, on a line of its own. */
    public void insertAttachment(@androidx.annotation.NonNull String markdown) {
        if (markdownEdit == null) return;
        Attachments.Insertion insertion = Attachments.insert(markdownEdit.getText().toString(),
                markdownEdit.getSelectionStart(), markdownEdit.getSelectionEnd(), markdown);
        markdownEdit.setText(insertion.text);
        markdownEdit.setSelection(Math.min(insertion.cursor, markdownEdit.length()));
        markdownEdit.requestFocus();
    }
}
