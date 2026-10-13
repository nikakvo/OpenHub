

package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;

import androidx.appcompat.widget.PopupMenu;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.INotificationsContract;
import com.thirtydegreesray.openhub.mvp.model.Notification;
import com.thirtydegreesray.openhub.mvp.model.NotificationSubject;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.NotificationsPresenter;
import com.thirtydegreesray.openhub.ui.activity.CommitDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.ActionsActivity;
import com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.PullRequestActivity;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.ui.adapter.NotificationsAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.DoubleTypesModel;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.NotificationLinks;

import java.util.ArrayList;

/**
 * Created by ThirtyDegreesRay on 2017/11/6 20:54:31
 */

public class NotificationsFragment extends ListFragment<NotificationsPresenter, NotificationsAdapter>
        implements INotificationsContract.View, NotificationsAdapter.NotificationAdapterListener {

    public enum NotificationsType{
        Unread, Participating, All
    }

    public static Fragment create(NotificationsType type){
        NotificationsFragment fragment = new NotificationsFragment();
        fragment.setArguments(BundleHelper.builder().put("type", type).build());
        return fragment;
    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(true);
        setHasOptionsMenu(NotificationsType.Unread.equals(mPresenter.getType()));
        adapter.setListener(this);
    }

    @Override
    public void showNotifications(ArrayList<DoubleTypesModel<Repository, Notification>> notifications) {
        adapter.setData(notifications);
        postNotifyDataSetChanged();
        getActivity().invalidateOptionsMenu();
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
        mPresenter.loadNotifications(1, true);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.no_notifications_tip);
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(mPresenter != null) mPresenter.prepareLoadData();
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        Notification notification = adapter.getData().get(position).getM2();
        Repository repository = adapter.getData().get(position).getM1();
        if(adapter.getItemViewType(position) == 0){
            RepositoryActivity.show(getActivity(), repository.getOwner().getLogin(), repository.getName());
        }else{
            open(notification);

            if(notification.isUnread()){
                mPresenter.markNotificationAsRead(notification.getId());
                notification.setUnread(false);
                adapter.notifyItemChanged(position);
                getActivity().invalidateOptionsMenu();
            }

        }

    }

    /** Opens what the notification is about, in the app where possible. */
    private void open(@NonNull Notification notification) {
        NotificationSubject subject = notification.getSubject();
        String url = subject == null ? null : subject.getUrl();
        NotificationSubject.Type type = subject == null ? null : subject.getType();
        if (type == NotificationSubject.Type.Issue && url != null) {
            IssueDetailActivity.show(getActivity(), url);
        } else if (type == NotificationSubject.Type.Commit && url != null) {
            CommitDetailActivity.show(getActivity(), url);
        } else if (type == NotificationSubject.Type.PullRequest && url != null
                && PullRequestActivity.createIntent(requireContext(), url) != null) {
            startActivity(PullRequestActivity.createIntent(requireContext(), url));
        } else if (type == NotificationSubject.Type.CheckSuite) {
            Repository repository = notification.getRepository();
            ActionsActivity.show(requireActivity(), repository.getOwner().getLogin(), repository.getName(),
                    null, false);
        } else {
            AppOpener.launchUrl(requireContext(), Uri.parse(NotificationLinks.htmlUrl(notification)));
        }
    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        if (adapter.getItemViewType(position) == 0) return super.onItemLongClick(position, view);
        Notification notification = adapter.getData().get(position).getM2();
        PopupMenu menu = new PopupMenu(requireContext(), view, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (notification.isUnread()) menu.getMenu().add(0, 1, 0, R.string.notification_mark_read);
        menu.getMenu().add(0, 2, 1, R.string.notification_mark_done);
        menu.getMenu().add(0, 3, 2, R.string.notification_unsubscribe);
        menu.getMenu().add(0, 4, 3, R.string.open_in_browser);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    mPresenter.markNotificationAsRead(notification.getId());
                    notification.setUnread(false);
                    adapter.notifyItemChanged(position);
                    requireActivity().invalidateOptionsMenu();
                    break;
                case 2:
                    mPresenter.markNotificationAsDone(notification);
                    break;
                case 3:
                    mPresenter.unsubscribe(notification);
                    showInfoToast(getString(R.string.notification_unsubscribed));
                    break;
                default:
                    AppOpener.openInCustomTabsOrBrowser(requireContext(), NotificationLinks.htmlUrl(notification));
            }
            return true;
        });
        menu.show();
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if(!mPresenter.isNotificationsAllRead()){
            inflater.inflate(R.menu.menu_notifications, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_mark_all_as_read){
            mPresenter.markAllNotificationsAsRead();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRepoMarkAsReadClicked(@NonNull Repository repository) {
        mPresenter.markRepoNotificationsAsRead(repository);
    }

}
