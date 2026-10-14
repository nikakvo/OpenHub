package com.thirtydegreesray.openhub.ui.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.WorkflowRun;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.RunStatus;
import com.thirtydegreesray.openhub.util.StringUtils;

import javax.inject.Inject;

/**
 * Workflow runs: title, workflow / run number / branch, status and time.
 */
public class ActionsAdapter extends BaseAdapter<ActionsAdapter.ViewHolder, WorkflowRun> {


    @Inject
    public ActionsAdapter(Context context, BaseFragment fragment) {
        super(context, fragment);
    }

    @Override
    protected int getLayoutId(int viewType) {
        return R.layout.layout_item_action_run;
    }

    @Override
    protected ViewHolder getViewHolder(View itemView, int viewType) {
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        WorkflowRun run = data.get(position);

        String title = StringUtils.isBlank(run.getDisplayTitle()) ? run.getName() : run.getDisplayTitle();
        holder.title.setText(title);

        StringBuilder workflow = new StringBuilder();
        if (!StringUtils.isBlank(run.getName())) workflow.append(run.getName()).append(" ");
        workflow.append("#").append(run.getRunNumber());
        if (!StringUtils.isBlank(run.getHeadBranch())) workflow.append(" · ").append(run.getHeadBranch());
        if (!StringUtils.isBlank(run.getEvent())) workflow.append(" · ").append(run.getEvent());
        holder.workflow.setText(workflow);

        RunStatus status = RunStatus.of(run.getStatus(), run.getConclusion());
        status.paintDot(holder.statusDot);
        status.paintLabel(holder.status);

        holder.time.setText(run.getCreatedAt() == null ? ""
                : StringUtils.getNewsTimeStr(context, run.getCreatedAt()));
    }

    class ViewHolder extends BaseViewHolder {
        View statusDot;
        TextView title;
        TextView workflow;
        TextView status;
        TextView time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            statusDot = root.findViewById(R.id.status_dot);
            title = root.findViewById(R.id.run_title);
            workflow = root.findViewById(R.id.run_workflow);
            status = root.findViewById(R.id.run_status);
            time = root.findViewById(R.id.run_time);
        }
    }
}
