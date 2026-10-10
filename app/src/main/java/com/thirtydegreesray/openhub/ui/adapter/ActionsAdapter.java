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
import com.thirtydegreesray.openhub.util.StringUtils;

import javax.inject.Inject;

/**
 * Workflow runs: title, workflow / run number / branch, status and time.
 */
public class ActionsAdapter extends BaseAdapter<ActionsAdapter.ViewHolder, WorkflowRun> {

    private static final int GREEN = 0xFF2DA44E;
    private static final int RED = 0xFFCF222E;
    private static final int AMBER = 0xFFBF8700;
    private static final int GREY = 0xFF8C959F;

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

        int color;
        int label;
        if ("completed".equals(run.getStatus())) {
            String conclusion = run.getConclusion() == null ? "" : run.getConclusion();
            switch (conclusion) {
                case "success":
                    color = GREEN; label = R.string.actions_run_success; break;
                case "failure":
                    color = RED; label = R.string.actions_run_failure; break;
                case "timed_out":
                    color = RED; label = R.string.actions_run_timed_out; break;
                case "action_required":
                    color = AMBER; label = R.string.actions_run_action_required; break;
                case "skipped":
                    color = GREY; label = R.string.actions_run_skipped; break;
                default:
                    color = GREY; label = R.string.actions_run_cancelled; break;
            }
        } else if ("in_progress".equals(run.getStatus())) {
            color = AMBER;
            label = R.string.actions_run_in_progress;
        } else {
            color = AMBER;
            label = R.string.actions_run_queued;
        }
        setStatus(holder, color, label);

        holder.time.setText(run.getCreatedAt() == null ? ""
                : StringUtils.getNewsTimeStr(context, run.getCreatedAt()));
    }

    private void setStatus(ViewHolder holder, @ColorInt int color, @StringRes int label) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        holder.statusDot.setBackground(dot);
        holder.status.setText(label);
        holder.status.setTextColor(color);
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
