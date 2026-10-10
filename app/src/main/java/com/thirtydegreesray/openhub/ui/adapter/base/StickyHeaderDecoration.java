package com.thirtydegreesray.openhub.ui.adapter.base;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Sticky section headers for a vertical RecyclerView. Each group of items with the same
 * header id gets a header above its first item, and the header of the top group stays
 * pinned until the next one pushes it out. Replaces the unmaintained
 * timehop stickyheadersrecyclerview library.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

    public interface Adapter<VH extends RecyclerView.ViewHolder> {
        /** Same id for all items of one section; a negative id means no header. */
        long getHeaderId(int position);

        VH onCreateHeaderViewHolder(ViewGroup parent);

        void onBindHeaderViewHolder(VH holder, int position);
    }

    private final Adapter<RecyclerView.ViewHolder> adapter;
    private final LongSparseArray<View> headers = new LongSparseArray<>();

    @SuppressWarnings("unchecked")
    public StickyHeaderDecoration(@NonNull Adapter<? extends RecyclerView.ViewHolder> adapter) {
        this.adapter = (Adapter<RecyclerView.ViewHolder>) adapter;
        if (adapter instanceof RecyclerView.Adapter) {
            ((RecyclerView.Adapter<?>) adapter).registerAdapterDataObserver(
                    new RecyclerView.AdapterDataObserver() {
                        @Override
                        public void onChanged() {
                            headers.clear();
                        }
                    });
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (!isValid(parent, position)) return;
        if (adapter.getHeaderId(position) >= 0 && isFirstInGroup(position)) {
            outRect.top = getHeader(parent, position).getHeight();
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        int left = parent.getPaddingLeft();
        int minTop = parent.getPaddingTop();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (!isValid(parent, position)) continue;
            long headerId = adapter.getHeaderId(position);
            if (headerId < 0) continue;
            boolean pinned = i == 0;
            if (!pinned && !isFirstInGroup(position)) continue;

            View header = getHeader(parent, position);
            int top = child.getTop() - header.getHeight();
            if (pinned) {
                top = Math.max(minTop, top);
                // let the next section's header push this one up
                for (int j = i + 1; j < childCount; j++) {
                    View next = parent.getChildAt(j);
                    int nextPosition = parent.getChildAdapterPosition(next);
                    if (!isValid(parent, nextPosition)) continue;
                    long nextId = adapter.getHeaderId(nextPosition);
                    if (nextId != headerId && nextId >= 0) {
                        int nextHeaderTop = next.getTop()
                                - getHeader(parent, nextPosition).getHeight();
                        top = Math.min(top, nextHeaderTop - header.getHeight());
                        break;
                    }
                }
            }
            canvas.save();
            canvas.translate(left, top);
            header.draw(canvas);
            canvas.restore();
        }
    }

    private boolean isValid(RecyclerView parent, int position) {
        return position != RecyclerView.NO_POSITION && parent.getAdapter() != null
                && position < parent.getAdapter().getItemCount();
    }

    private boolean isFirstInGroup(int position) {
        return position == 0 || adapter.getHeaderId(position) != adapter.getHeaderId(position - 1);
    }

    private View getHeader(RecyclerView parent, int position) {
        long headerId = adapter.getHeaderId(position);
        View header = headers.get(headerId);
        if (header != null) return header;

        RecyclerView.ViewHolder holder = adapter.onCreateHeaderViewHolder(parent);
        adapter.onBindHeaderViewHolder(holder, position);
        header = holder.itemView;
        if (header.getLayoutParams() == null) {
            header.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(width, 0), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        ViewGroup.LayoutParams lp = header.getLayoutParams();
        header.measure(ViewGroup.getChildMeasureSpec(widthSpec, 0, lp.width),
                ViewGroup.getChildMeasureSpec(heightSpec, 0, lp.height));
        header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());
        if (width > 0) headers.put(headerId, header);
        return header;
    }

}
