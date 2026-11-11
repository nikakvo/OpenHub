package com.thirtydegreesray.openhub.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.mvp.model.IssueEvent;
import com.thirtydegreesray.openhub.mvp.model.Label;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.ui.widget.CircleBackgroundImageView;
import com.thirtydegreesray.openhub.ui.widget.IssueLabelSpan;
import com.thirtydegreesray.openhub.util.MarkdownRenderer;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.QuoteStyle;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.WindowUtil;

import java.util.ArrayList;

import javax.inject.Inject;

import com.thirtydegreesray.openhub.ui.widget.CircleImageView;
import io.noties.markwon.Markwon;

/**
 * Created by ThirtyDegreesRay on 2017/9/27 16:06:43
 */

public class IssueTimelineAdapter extends BaseAdapter<BaseViewHolder, IssueEvent> {

    private final int TYPE_HEAD_COMMENT = 1;
    private final int TYPE_COMMENT = 2;
    private final int TYPE_EVENT = 3;

    private Markwon markwon;
    /**
     * Each comment is rendered once and kept, with the pictures it has loaded: a row that is
     * bound again (scrolling, or after a picture arrived) shows the same text at once.
     * Rendering it again started every picture as an empty space of height 0, so the rows
     * shrank and grew again (flicker).
     */
    private final java.util.Map<String, android.text.Spanned> rendered = new java.util.HashMap<>();

    @Inject
    public IssueTimelineAdapter(Context context, BaseFragment fragment) {
        super(context, fragment);
    }

    private RecyclerView recyclerView;

    /**
     * The comments are rendered from their Markdown like pull requests and discussions,
     * with pictures. When a picture arrives, the texts on screen are set again in place so
     * they take its height. The rows are not bound again for that: a comment that moves to
     * another row cancels the picture still loading in the old row, and it has to start
     * over in the new one.
     */
    @NonNull
    private Markwon markwon() {
        if (markwon == null) {
            markwon = MarkdownRenderer.createPreview(context,
                    () -> MarkdownRenderer.refreshImages(recyclerView));
        }
        return markwon;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    /**
     * A row that left the screen stopped its pictures (Markwon cancels them when the text
     * is detached); a row that comes back without being bound again starts them again.
     */
    @Override
    public void onViewAttachedToWindow(@NonNull BaseViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof CommentViewHolder && ((CommentViewHolder) holder).commentDesc != null) {
            io.noties.markwon.image.AsyncDrawableScheduler.schedule(((CommentViewHolder) holder).commentDesc);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if(data.get(position).getParentIssue() != null){
            return TYPE_HEAD_COMMENT;
        }
        switch (data.get(position).getType()) {
            case commented:
                return TYPE_COMMENT;
            default:
                return TYPE_EVENT;
        }
    }

    @Override
    protected int getLayoutId(int viewType) {
        switch (viewType) {
            case TYPE_HEAD_COMMENT:
                return R.layout.layout_item_head_comments;
            case TYPE_COMMENT:
                return R.layout.layout_item_comments;
            default:
                return R.layout.layout_item_event;
        }
    }

    @Override
    protected BaseViewHolder getViewHolder(View itemView, int viewType) {
        switch (viewType) {
            case TYPE_HEAD_COMMENT:
                return new HeadCommentViewHolder(itemView);
            case TYPE_COMMENT:
                return new CommentViewHolder(itemView);
            default:
                return new EventViewHolder(itemView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        IssueEvent model = data.get(position);
        switch (getItemViewType(position)) {
            case TYPE_COMMENT:
                ((CommentViewHolder) holder).setData(model);
                break;
            case TYPE_HEAD_COMMENT:
                ((HeadCommentViewHolder) holder).setData(model);
                break;
            default:
                ((EventViewHolder) holder).setData(model, position);
                break;
        }

    }

    class HeadCommentViewHolder extends CommentViewHolder{
        TextView issueLabels;
        TextView issueMeta;
        LinearLayout subIssues;
        LinearLayout issueFields;
        public HeadCommentViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        void setData(IssueEvent model) {
            super.setData(model);
            showMeta(model.getParentIssue());
            if (fragment.getActivity() instanceof com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity) {
                ((com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity) fragment.getActivity())
                        .subIssuesBox().bind(subIssues);
                ((com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity) fragment.getActivity())
                        .issueFieldsBox().bind(issueFields);
            } else {
                subIssues.setVisibility(View.GONE);
                issueFields.setVisibility(View.GONE);
            }
            ArrayList<Label> labels = model.getParentIssue().getLabels();
            if(labels == null || labels.size() == 0){
                issueLabels.setVisibility(View.GONE);
                return;
            } else {
                issueLabels.setVisibility(View.VISIBLE);
            }
            issueLabels.setText(ViewUtils.getLabelsSpan(context, labels));
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            issueLabels = root.findViewById(R.id.issue_labels);
            issueMeta = root.findViewById(R.id.issue_meta);
            subIssues = root.findViewById(R.id.issue_sub_issues);
            issueFields = root.findViewById(R.id.issue_fields);
        }

        /** "Assignees: a, b · Milestone: v1.0" */
        private void showMeta(@NonNull com.thirtydegreesray.openhub.mvp.model.Issue issue) {
            StringBuilder text = new StringBuilder();
            if (issue.getAssignees() != null && !issue.getAssignees().isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (com.thirtydegreesray.openhub.mvp.model.User user : issue.getAssignees()) {
                    if (names.length() > 0) names.append(", ");
                    names.append(user.getLogin());
                }
                text.append(context.getString(R.string.issue_meta_assignees, names));
            }
            if (issue.getMilestone() != null && issue.getMilestone().getTitle() != null) {
                if (text.length() > 0) text.append(" · ");
                text.append(context.getString(R.string.issue_meta_milestone, issue.getMilestone().getTitle()));
            }
            issueMeta.setText(text);
            issueMeta.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
        }
    }

    class CommentViewHolder extends BaseViewHolder {

        CircleImageView userAvatar;
        TextView userName;
        TextView time;
        TextView commentDesc;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void onUserClicked() {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                ProfileActivity.show((Activity) context, userAvatar, data.get(getAdapterPosition()).getUser().getLogin(),
                        data.get(getAdapterPosition()).getUser().getAvatarUrl());
            }
        }

        void setData(IssueEvent model) {
            GlideApp.with(fragment)
                    .load(model.getUser().getAvatarUrl())
                    .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                    .into(userAvatar);
            userName.setText(model.getUser().getLogin());
            time.setText(StringUtils.getNewsTimeStr(context, model.getCreatedAt()));
            // a preview of 20 lines as before (a tap opens the whole comment); rows are
            // reused, so every branch starts from it
            commentDesc.setMaxLines(20);
            commentDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (PrefUtils.isLoadImageEnable() && !StringUtils.isBlank(model.getBody())) {
                String key = model.getId() + "\n" + model.getBody();
                android.text.Spanned text = rendered.get(key);
                if (text == null) {
                    text = markwon().toMarkdown(model.getBody());
                    rendered.put(key, text);
                }
                // the whole comment, like pull requests: with "maxLines" and an ellipsis
                // Android sometimes put "..." in place of a picture as wide as the text
                commentDesc.setEllipsize(null);
                commentDesc.setMaxLines(Integer.MAX_VALUE);
                markwon().setParsedMarkdown(commentDesc, text);
            } else if (!StringUtils.isBlank(model.getBodyHtml())) {
                // pictures switched off in Settings: GitHub's HTML without pictures
                commentDesc.setText(QuoteStyle.apply(context, Html.fromHtml(model.getBodyHtml())));
            } else if (!StringUtils.isBlank(model.getBody())) {
                commentDesc.setText(model.getBody());
            } else {
                commentDesc.setText(R.string.no_description);
            }
            //cause android:ellipsize="end" invalid
//        commentDesc.setMovementMethod(LinkMovementMethod.getInstance());
            //TODO text bottom spacing too large
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            userAvatar = root.findViewById(R.id.user_avatar);
            userName = root.findViewById(R.id.user_name);
            time = root.findViewById(R.id.time);
            commentDesc = root.findViewById(R.id.comment_desc);
            ViewUtils.setOnClick(root, R.id.user_avatar, v -> onUserClicked());
            ViewUtils.setOnClick(root, R.id.user_name, v -> onUserClicked());
        }
    }

    class EventViewHolder extends BaseViewHolder{
        LinearLayout rootLay;
        CircleBackgroundImageView eventIcon;
        CircleImageView userAvatar;
        TextView desc;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void onUserClicked() {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                ProfileActivity.show((Activity) context, userAvatar, data.get(getAdapterPosition()).getActor().getLogin(),
                        data.get(getAdapterPosition()).getActor().getAvatarUrl());
            }
        }

        void setData(IssueEvent model, int position) {
            GlideApp.with(fragment)
                .load(model.getActor().getAvatarUrl())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(userAvatar);
            setEventIcon(model);
            setDesc(model);
            setMargin(model, position);
        }

        void setDesc(IssueEvent model){
            SpannableStringBuilder text = new SpannableStringBuilder(model.getActor().getLogin());
            text.append(" ");
            String info;
            switch (model.getType()){
                case reopened:
                    text.append(getString(R.string.issue_reopened));
                    break;
                case closed:
                    text.append(getString(R.string.issue_close));
                    break;
                case renamed:
                    text.append(getString(R.string.issue_modified));
                    break;
                case locked:
                    text.append(getString(R.string.issue_locked_conversation));
                    break;
                case unlocked:
                    text.append(getString(R.string.issue_unlocked_conversation));
                    break;
                case crossReferenced:
                    if(model.getSource().getType() != null){
                        info = String.format(getString(R.string.issue_referenced),
                                "#" + model.getSource().getIssue().getTitle());
                        text.append(info);
                    }
                    break;
                case assigned:
                    if (model.getAssignee() != null) {
                        info = String.format(getString(R.string.issue_assigned), model.getAssignee().getLogin());
                        text.append(info);
                    }
                    break;
                case unassigned:
                    text.append(getString(R.string.issue_unassigned));
                    break;
                case milestoned:
                    info = String.format(getString(R.string.issue_added_to_milestone),
                            model.getMilestone().getTitle());
                    text.append(info);
                    break;
                case demilestoned:
                    info = String.format(getString(R.string.issue_removed_from_milestone),
                            model.getMilestone().getTitle());
                    text.append(info);
                    break;
                case commentDeleted:
                    text.append(getString(R.string.issue_delete_comment));
                    break;
                case labeled:
                    info = String.format(getString(R.string.issue_add_label), "[label]");
                    text.append(info);
                    break;
                case unlabeled:
                    info = String.format(getString(R.string.issue_remove_label), "[label]");
                    text.append(info);
                    break;
                default:
//                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.transparent));
                    break;
            }

            int labelPos = text.toString().indexOf("[label]");
            Label label = model.getLabel();
            if(label != null && labelPos >= 0){
                text.replace(labelPos, labelPos + 7, label.getName());
                text.setSpan(new IssueLabelSpan(context, label),
                        labelPos, labelPos + label.getName().length(), 0);
            }

            String timeStr = StringUtils.getNewsTimeStr(context, model.getCreatedAt());
            text.append(" ").append(timeStr);
            desc.setText(text);
        }

        void setMargin(IssueEvent model, int position){
            int topMargin = 0;
            int bottomMargin = 0;
            if(position - 1 > 0 && data.get(position - 1).getType().equals(IssueEvent.Type.commented)){
                topMargin = (int) context.getResources().getDimension(R.dimen.spacing_mini);
            }
            if(position + 1 < data.size() && data.get(position +1).getType().equals(IssueEvent.Type.commented)){
                bottomMargin = (int) context.getResources().getDimension(R.dimen.spacing_mini);
            }
            if(data.get(position).getType().equals(IssueEvent.Type.closed)){
                bottomMargin = (int) context.getResources().getDimension(R.dimen.spacing_normal);
            }
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) rootLay.getLayoutParams();
            layoutParams.setMargins(0, topMargin, 0, bottomMargin);
            rootLay.setLayoutParams(layoutParams);
        }

        void setEventIcon(IssueEvent model){
            switch (model.getType()){
                case reopened:
                    eventIcon.setImageResource(R.drawable.ic_dot);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.commit_file_added_color));
                    setEventIconPadding(8);
                    break;
                case closed:
                    eventIcon.setImageResource(R.drawable.ic_block);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_red_800));
                    recoverEventIconPadding();
                    break;
                case renamed:
                    eventIcon.setImageResource(R.drawable.ic_edit);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;
                case locked:
                    eventIcon.setImageResource(R.drawable.ic_lock);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.black_light));
                    recoverEventIconPadding();
                    break;
                case unlocked:
                    eventIcon.setImageResource(R.drawable.ic_unlock);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.commit_file_added_color));
                    recoverEventIconPadding();
                    break;
                case crossReferenced:
                    eventIcon.setImageResource(R.drawable.ic_quote);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;
                case assigned:
                case unassigned:
                    eventIcon.setImageResource(R.drawable.ic_menu_person);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;
                case milestoned:
                case demilestoned:
                    eventIcon.setImageResource(R.drawable.ic_milestone);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;
                case commentDeleted:
                    eventIcon.setImageResource(R.drawable.ic_delete);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;
                case labeled:
                case unlabeled:
                    eventIcon.setImageResource(R.drawable.ic_label);
                    eventIcon.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.material_grey_500));
                    recoverEventIconPadding();
                    break;

                default:
                    eventIcon.setBackgroundColor(context.getResources().getColor(R.color.transparent));
                    break;
            }
        }

        void recoverEventIconPadding(){
            setEventIconPadding(4);
        }

        void setEventIconPadding(int padding){
            int paddingInPx = WindowUtil.dipToPx(context, padding);
            eventIcon.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx);
        }


        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            rootLay = root.findViewById(R.id.root_lay);
            eventIcon = root.findViewById(R.id.event_icon);
            userAvatar = root.findViewById(R.id.user_avatar);
            desc = root.findViewById(R.id.desc);
            ViewUtils.setOnClick(root, R.id.user_avatar, v -> onUserClicked());
        }
    }

}
