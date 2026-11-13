package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;

/**
 * A dialog with a spinner and a message while something runs, in the dialog theme of
 * the app. Replaces the deprecated android.app.ProgressDialog.
 */
public class LoadingDialog extends AlertDialog {

    @Nullable private TextView messageView;
    @Nullable private CharSequence message;

    public LoadingDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_loading, null);
        messageView = view.findViewById(R.id.loading_message);
        messageView.setText(message);
        setView(view);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setMessage(@Nullable CharSequence message) {
        this.message = message;
        if (messageView != null) messageView.setText(message);
    }
}
