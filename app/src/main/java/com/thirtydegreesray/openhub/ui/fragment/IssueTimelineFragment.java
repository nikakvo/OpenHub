package com.thirtydegreesray.openhub.ui.fragment;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.View;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IIssueTimelineContract;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.IssueEvent;
import com.thirtydegreesray.openhub.mvp.presenter.IssueTimelinePresenter;
import com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.MarkdownEditorActivity;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;
import com.thirtydegreesray.openhub.ui.adapter.IssueTimelineAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.CommentQuote;
import com.thirtydegreesray.openhub.util.BundleHelper;

import java.util.ArrayList;

/**
 * Created by ThirtyDegreesRay on 2017/9/27 16:32:49
 */

public class IssueTimelineFragment extends ListFragment<IssueTimelinePresenter, IssueTimelineAdapter>
        implements IIssueTimelineContract.View {

    public static IssueTimelineFragment create(@NonNull Issue issue){
        IssueTimelineFragment fragment = new IssueTimelineFragment();
        fragment.setArguments(BundleHelper.builder().put("issue", issue).build());
        return fragment;
    }

    @AutoAccess String editingCommentId;

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(true);
        setAutoJudgeCanLoadMoreEnable(false);
    }

    @Override
    public void showTimeline(ArrayList<IssueEvent> events) {
        adapter.setData(events);
        postNotifyDataSetChanged();
    }

    @Override
    public void showEditCommentPage(String commentId, String body) {
        Issue issue = mPresenter.getIssue();
        MarkdownEditorActivity.show(getActivity(), R.string.comment,
                IssueDetailActivity.EDIT_COMMENT_REQUEST_CODE, body, null,
                issue == null ? null : issue.getRepoAuthorName(), issue == null ? null : issue.getRepoName(),
                issue == null ? null : issue.getHtmlUrl());
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_list;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {
        DaggerFragmentComponent.builder()
                .appComponent(appComponent)
                .fragmentModule(new FragmentModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadTimeline(1, true);
        if (getActivity() instanceof IssueDetailActivity) {
            ((IssueDetailActivity) getActivity()).reloadIssue();
        }
    }

    /** The issue was loaded again (pull down): its text at the top of the timeline. */
    public void issueReloaded(@NonNull Issue issue) {
        if (mPresenter == null) return;
        mPresenter.setIssue(issue);
        if (adapter == null || adapter.getData() == null || adapter.getData().isEmpty()) return;
        IssueEvent first = adapter.getData().get(0);
        if (first.getParentIssue() == null) return;
        first.setBody(issue.getBody());
        first.setBodyHtml(issue.getBodyHtml());
        first.setParentIssue(issue);
        adapter.notifyItemChanged(0);
    }

    /** The parent or the sub-issues changed: draw the first item again. */
    public void subIssuesChanged() {
        if (adapter == null || adapter.getData() == null || adapter.getData().isEmpty()) return;
        if (adapter.getData().get(0).getParentIssue() == null) return;
        adapter.notifyItemChanged(0);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.no_comments);
    }

    @Override
    protected void onLoadMore(int page) {
        super.onLoadMore(page);
        mPresenter.loadTimeline(page, false);
    }

    @Override
    protected int getHeaderSize() {
        return 1;
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        if(IssueEvent.Type.commented.equals(adapter.getData().get(position).getType())){
            ViewerActivity.showMdSource(getActivity(), getString(R.string.comment),
                    adapter.getData().get(position).getBodyHtml());
        }
    }

    public void addComment(IssueEvent event){
        mPresenter.getTimeline().add(event);
        postNotifyDataSetChanged();
        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public boolean onItemLongClick(final int position, @NonNull View view) {
        final IssueEvent issueEvent = adapter.getData().get(position);
        if(!IssueEvent.Type.commented.equals(issueEvent.getType())){
            return true;
        }

        // Quote reply, Mention @login, Share, then Edit and Delete on your own comments
        final IssueDetailActivity page = getActivity() instanceof IssueDetailActivity
                ? (IssueDetailActivity) getActivity() : null;
        final boolean canWrite = page != null && page.canComment();
        final String login = issueEvent.getUser() == null ? null : issueEvent.getUser().getLogin();
        final String quote = CommentQuote.quote(issueEvent.getBody());
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (canWrite && !quote.isEmpty()) {
            labels.add(getString(R.string.comment_quote_reply));
            ids.add(1);
        }
        String me = AppData.INSTANCE.getLoggedUser() == null ? null : AppData.INSTANCE.getLoggedUser().getLogin();
        if (canWrite && CommentQuote.canMention(login, me, false)) {
            labels.add(getString(R.string.comment_mention, login));
            ids.add(2);
        }
        labels.add(getString(R.string.share));
        ids.add(3);
        if(mPresenter.isEditAndDeleteEnable(position) && position != 0){
            labels.add(getString(R.string.edit));
            ids.add(4);
            labels.add(getString(R.string.delete));
            ids.add(5);
        }
        new AlertDialog.Builder(getActivity())
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (ids.get(which)){
                            case 1:
                                page.showAddCommentPage(CommentQuote.addQuote(page.commentDraft(), quote));
                                break;
                            case 2:
                                page.showAddCommentPage(CommentQuote.addMention(page.commentDraft(), login));
                                break;
                            case 3:
                                AppOpener.shareText(getActivity(), issueEvent.getHtmlUrl());
                                break;
                            case 4:
                                editingCommentId = issueEvent.getId();
                                showEditCommentPage(editingCommentId, issueEvent.getBody());
                                break;
                            case 5:
                                showDeleteCommentWarning(position, issueEvent.getId());
                                break;
                        }
                    }
                })
                .show();
        return true;
    }

    private void showDeleteCommentWarning(final int position, final String commentId){
        new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(R.string.delete_comment_warning)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        adapter.getData().remove(position);
                        adapter.notifyItemRemoved(position);
                        mPresenter.deleteComment(commentId);
                    }
                })
                .show();
    }

    public void onEditComment(String body) {
        mPresenter.editComment(editingCommentId, body);
    }

    public void onEditIssue(Issue issue){
        mPresenter.setIssue(issue);
        adapter.getData().get(0).setBody(issue.getBody());
        adapter.getData().get(0).setBodyHtml(issue.getBodyHtml());
        adapter.getData().get(0).setParentIssue(issue);
        adapter.notifyItemChanged(0);
        // the edit adds events (labels, assignees, milestone): load them from the network;
        // GitHub lists them a moment after the change
        if (getView() != null) {
            getView().postDelayed(() -> {
                if (isAdded() && mPresenter != null) mPresenter.loadTimeline(1, true);
            }, 1500);
        }
    }

    public ArrayList<String> getIssueUsersExceptMe(){
        return mPresenter.getIssueUsersExceptMe();
    }

}
