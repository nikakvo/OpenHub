

package com.thirtydegreesray.openhub.ui.fragment.base;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.adapter.base.CatchableLinearLayoutManager;
import com.thirtydegreesray.openhub.util.NetHelper;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import javax.inject.Inject;


/**
 * Created on 2017/7/20.
 *
 * @author ThirtyDegreesRay
 */

public abstract class ListFragment <P extends IBaseContract.Presenter, A extends BaseAdapter>
        extends BaseFragment<P> implements IBaseContract.View,
        BaseViewHolder.OnItemClickListener,
        BaseViewHolder.OnItemLongClickListener,
        SwipeRefreshLayout.OnRefreshListener{

    protected SwipeRefreshLayout refreshLayout;
    protected RecyclerView recyclerView;
    @Nullable private FloatingActionButton scrollTopBn;
    private static final int SCROLL_TOP_THRESHOLD = 4;
    @Inject protected A adapter;
    private RecyclerView.AdapterDataObserver observer;

    LinearLayout layTip;
    TextView tvTip;
    AppCompatImageView errorImage;

    private int curPage = 1;

    private boolean refreshEnable = true;
    private boolean loadMoreEnable = false;
    private boolean canLoadMore = false;
    private boolean autoJudgeCanLoadMoreEnable = true;
    private boolean isLoading = false;
    private final int DEFAULT_PAGE_SIZE = 30;

    /** When the list was last loaded (elapsedRealtime), 0 before the first load. */
    private long lastLoadedAt;
    /** {@link #changeStamp()} when the last load started. */
    private long seenStamp;

    /** For feeds that change all the time. */
    protected static final long AUTO_REFRESH_FEED_MS = 3 * 60 * 1000L;
    /** For lists that change now and then. */
    protected static final long AUTO_REFRESH_LIST_MS = 10 * 60 * 1000L;
    /** For curated lists that hardly ever change. */
    protected static final long AUTO_REFRESH_STATIC_MS = 60 * 60 * 1000L;

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        refreshLayout.setOnRefreshListener(this);
        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(getContext()));

        recyclerView.setLayoutManager(new CatchableLinearLayoutManager(getActivity()));
        adapter.setOnItemLongClickListener(this);
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        layTip.setVisibility(View.GONE);

        //adapter 数据观察者，当数据为空时，显示空提示
        observer = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                int itemCount = adapter.getItemCount();
                if (itemCount == 0) {
                    refreshLayout.setVisibility(View.GONE);
                    layTip.setVisibility(View.VISIBLE);
                    tvTip.setText(getEmptyTip());
                    errorImage.setVisibility(View.GONE);
                } else {
                    refreshLayout.setVisibility(View.VISIBLE);
                    layTip.setVisibility(View.GONE);
                    itemCount -= getHeaderSize();
                    if(loadMoreEnable && autoJudgeCanLoadMoreEnable){
                        canLoadMore = itemCount % getPagerSize() == 0 ;
//                        curPage = itemCount % getPagerSize() == 0 ?
//                                itemCount / getPagerSize() : (itemCount / getPagerSize()) + 1;
                    }
                }
            }

            // a single item added to or removed from the list (a new issue on an empty
            // list, the last item deleted) must switch between the list and the empty tip too
            @Override
            public void onItemRangeInserted(int positionStart, int count) {
                updateEmptyTip();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int count) {
                updateEmptyTip();
            }

            private void updateEmptyTip() {
                if (adapter.getItemCount() == 0) {
                    refreshLayout.setVisibility(View.GONE);
                    layTip.setVisibility(View.VISIBLE);
                    tvTip.setText(getEmptyTip());
                    errorImage.setVisibility(View.GONE);
                } else {
                    refreshLayout.setVisibility(View.VISIBLE);
                    layTip.setVisibility(View.GONE);
                }
            }
        };
        adapter.registerAdapterDataObserver(observer);
        recyclerView.setOnScrollListener(new ScrollListener());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateScrollTopButton();
            }
        });
        refreshLayout.setRefreshing(true);
        initScrollListener();
    }

    private class ScrollListener extends RecyclerView.OnScrollListener{
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if(!loadMoreEnable || !canLoadMore || isLoading ||
                    !NetHelper.INSTANCE.getNetEnabled()) return;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            //only LinearLayoutManager can find last visible
            if(layoutManager instanceof LinearLayoutManager){
                LinearLayoutManager linearManager = (LinearLayoutManager) layoutManager;
                int lastPosition = linearManager.findLastVisibleItemPosition();
                if(lastPosition == adapter.getItemCount() - 1){
                    onLoadMore(++curPage);
                }
            }
        }
    }

    private int getFirstVisiblePosition() {
        RecyclerView.LayoutManager layoutManager = recyclerView == null ? null : recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
        }
        return 0;
    }

    /** Long lists get a small arrow button that jumps back to the top. */
    private void updateScrollTopButton() {
        if (scrollTopBn == null) return;
        boolean show = getFirstVisiblePosition() > SCROLL_TOP_THRESHOLD;
        if (show && scrollTopBn.getVisibility() != View.VISIBLE) {
            avoidActivityFab();
            scrollTopBn.show();
        } else if (!show && scrollTopBn.getVisibility() == View.VISIBLE) {
            scrollTopBn.hide();
        }
    }

    /** Sits above the screen's own floating button (e.g. Actions "run") when there is one. */
    private void avoidActivityFab() {
        if (scrollTopBn == null || getActivity() == null) return;
        View activityFab = getActivity().findViewById(R.id.float_action_bn);
        int margin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
        if (activityFab != null && activityFab.getVisibility() == View.VISIBLE) {
            margin += activityFab.getHeight() + margin;
        }
        ViewGroup.LayoutParams lp = scrollTopBn.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams
                && ((ViewGroup.MarginLayoutParams) lp).bottomMargin != margin) {
            ((ViewGroup.MarginLayoutParams) lp).bottomMargin = margin;
            scrollTopBn.setLayoutParams(lp);
        }
    }

    private void onScrollTopClick() {
        if (recyclerView == null) return;
        // Jump close to the top first so long lists don't animate through hundreds of rows.
        if (getFirstVisiblePosition() > SCROLL_TOP_THRESHOLD * 2) {
            recyclerView.scrollToPosition(SCROLL_TOP_THRESHOLD);
        }
        recyclerView.smoothScrollToPosition(0);
    }

    public int getVisibleItemCount(){
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        //only LinearLayoutManager can find last visible
        if(layoutManager instanceof LinearLayoutManager){
            LinearLayoutManager linearManager = (LinearLayoutManager) layoutManager;
            int firstPosition = linearManager.findFirstVisibleItemPosition();
            int lastPosition = linearManager.findLastVisibleItemPosition();
            return lastPosition - firstPosition + 1;
        }else {
            throw new UnsupportedOperationException("only for Linear RecyclerView ");
        }
    }

    public int getItemCount(){
        return adapter.getItemCount();
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {

    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        return false;
    }

    @Override
    public void onRefresh() {
        refreshLayout.setRefreshing(true);
        curPage = 1;
        onReLoadData();
    }

    public void onRetryClick(@NonNull View view) {
        refreshLayout.setVisibility(View.VISIBLE);
        layTip.setVisibility(View.GONE);
        refreshLayout.setRefreshing(true);
        curPage = 1;
        onReLoadData();
    }

    protected void setErrorTip(String errorTip){
        refreshLayout.setVisibility(View.GONE);
        errorImage.setVisibility(View.VISIBLE);
        layTip.setVisibility(View.VISIBLE);
        tvTip.setText(errorTip);
    }

    protected void addVerticalDivider(){
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
    }

    /**
     * load more switch
     * @param loadMoreEnable flag
     */
    public void setLoadMoreEnable(boolean loadMoreEnable) {
        this.loadMoreEnable = loadMoreEnable;
    }

    public void setCanLoadMore(boolean canLoadMore) {
        this.canLoadMore = canLoadMore;
    }

    public void setRefreshEnable(boolean refreshEnable) {
        this.refreshEnable = refreshEnable;
        refreshLayout.setEnabled(refreshEnable);
    }

    public void setAutoJudgeCanLoadMoreEnable(boolean autoJudgeLoadMoreEnable) {
        this.autoJudgeCanLoadMoreEnable = autoJudgeLoadMoreEnable;
        canLoadMore = !autoJudgeLoadMoreEnable;
    }

    public int getCurPage() {
        return curPage;
    }

    @Override
    public void showLoading() {
        isLoading = true;
        seenStamp = changeStamp();
        refreshLayout.setRefreshing(true);
    }

    @Override
    public void hideLoading() {
        isLoading = false;
        lastLoadedAt = SystemClock.elapsedRealtime();
        refreshLayout.setRefreshing(false);
    }

    // ---- automatic refresh ----------------------------------------------------------
    // A list that comes back to the front (screen resumed, or its side menu tab shown
    // again) reloads by itself when its data was changed on another screen or when it is
    // older than autoRefreshAfterMs(), as if it had been pulled down.

    /**
     * Current change stamp of this list's data (e.g. {@code AccountChanges.starsStamp()}),
     * or 0 when other screens cannot change it. A different value than at the last load
     * means: reload.
     */
    protected long changeStamp() {
        return 0;
    }

    /** Reload when the list is older than this when shown again; 0 = never. */
    protected long autoRefreshAfterMs() {
        return 0;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) autoRefreshIfNeeded();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) autoRefreshIfNeeded();
    }

    private void autoRefreshIfNeeded() {
        if (refreshLayout == null || !refreshEnable || isLoading || lastLoadedAt == 0) return;
        long stamp = changeStamp();
        boolean changed = stamp != 0 && seenStamp != 0 && stamp != seenStamp;
        long maxAge = autoRefreshAfterMs();
        boolean old = maxAge > 0 && SystemClock.elapsedRealtime() - lastLoadedAt > maxAge;
        if (!changed && !old) return;
        if (!NetHelper.INSTANCE.getNetEnabled() && usesNetwork()) return; // keep what is shown
        onRefresh();
    }

    /** False for lists read from the phone's database (they reload offline too). */
    protected boolean usesNetwork() {
        return true;
    }

    protected abstract void onReLoadData();

    protected abstract String getEmptyTip();

    protected int getPagerSize(){
        return DEFAULT_PAGE_SIZE;
    }

    protected int getHeaderSize(){
        return 0;
    }

    protected void onLoadMore(int page){
        if(page == 3 && PrefUtils.isDoubleClickTitleTipAble()){
            showOperationTip(R.string.double_click_toolbar_tip);
            PrefUtils.set(PrefUtils.DOUBLE_CLICK_TITLE_TIP_ABLE, false);
        }
    }

    public void showLoadError(String error) {
        setErrorTip(error);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(adapter != null && observer != null)
            adapter.unregisterAdapterDataObserver(observer);
    }

    @Override
    public void scrollToTop() {
        super.scrollToTop();
        if(recyclerView != null) recyclerView.scrollToPosition(0);
    }

    protected void postNotifyDataSetChanged(){
        adapter.notifyDataSetChanged();
    }

    private ListScrollListener mListScrollListener;

    public void setListScrollListener(ListScrollListener listScrollListener){
        mListScrollListener = listScrollListener;
        initScrollListener();
    }

    private void initScrollListener(){
        if(recyclerView == null || mListScrollListener == null) return;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if(dy > 0){
                    mListScrollListener.onScrollUp();
                } else {
                    mListScrollListener.onScrollDown();
                }
            }
        });
    }

    public interface ListScrollListener{
        void onScrollUp();

        void onScrollDown();
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view);
        layTip = root.findViewById(R.id.lay_tip);
        tvTip = root.findViewById(R.id.tv_tip);
        errorImage = root.findViewById(R.id.error_image);
        scrollTopBn = root.findViewById(R.id.scroll_top_bn);
        if (scrollTopBn != null) scrollTopBn.setOnClickListener(v -> onScrollTopClick());
        ViewUtils.setOnClick(root, R.id.retry_bn, v -> onRetryClick(v));
    }
}
