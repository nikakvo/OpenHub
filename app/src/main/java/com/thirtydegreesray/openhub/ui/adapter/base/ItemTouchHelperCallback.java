package com.thirtydegreesray.openhub.ui.adapter.base;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

/**
 * Created by ThirtyDegreesRay on 2017/11/22 17:10:50
 */

public class ItemTouchHelperCallback extends ItemTouchHelper.SimpleCallback {

    private ItemGestureListener listener;


    public ItemTouchHelperCallback(int dragDirs, int swipeDirs, ItemGestureListener listener) {
        super(dragDirs, swipeDirs);
        this.listener = listener;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int from = viewHolder.getBindingAdapterPosition();
        int to = target.getBindingAdapterPosition();
        return listener.onItemMoved(from, to);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        listener.onItemSwiped(viewHolder.getBindingAdapterPosition(), direction);
    }

    public interface ItemGestureListener{
        boolean onItemMoved(int fromPosition, int toPosition);
        void onItemSwiped(int position, int direction);
    }

}
