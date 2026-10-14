

package com.thirtydegreesray.openhub.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.ILoginContract;
import com.thirtydegreesray.openhub.mvp.model.BasicToken;
import com.thirtydegreesray.openhub.mvp.presenter.LoginPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import androidx.appcompat.widget.AppCompatButton;

import es.dmoral.toasty.Toasty;
import com.thirtydegreesray.openhub.util.ViewUtils;
import android.view.View;

/**
 * Created on 2017/7/12.
 *
 * @author ThirtyDegreesRay
 */

public class LoginActivity extends BaseActivity<LoginPresenter>
        implements ILoginContract.View {

    private final String TAG = LoginActivity.class.getSimpleName();

//    @BindView(R2.id.user_name_et) TextInputEditText userNameEt;
//    @BindView(R2.id.user_name_layout) TextInputLayout userNameLayout;
//    @BindView(R2.id.password_et) TextInputEditText passwordEt;
//    @BindView(R2.id.password_layout) TextInputLayout passwordLayout;
    AppCompatButton loginBn;
    AppCompatButton tokenLoginBn;

//    private String userName;
//    private String password;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mPresenter.handleOauth(intent);
        setIntent(null);
    }

    @Override
    public void onGetTokenSuccess(BasicToken basicToken) {
        loginBn.setEnabled(false);
        mPresenter.getUserInfo(basicToken);
    }

    @Override
    public void onGetTokenError(String errorMsg) {
        loginBn.setEnabled(true);
        if (tokenLoginBn != null) tokenLoginBn.setEnabled(true);

        Toasty.error(getApplicationContext(), errorMsg).show();
    }

    @Override
    public void onLoginComplete() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * 依赖注入的入口
     *
     * @param appComponent appComponent
     */
    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    /**
     * 获取ContentView id
     *
     * @return
     */
    @Override
    protected int getContentView() {
        return R.layout.activity_login_new;
    }

    /**
     * 初始化view
     *
     * @param savedInstanceState
     */
    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        // Builds without an OAuth App (public.properties is empty by default) can only
        // log in with a personal access token.
        if (StringUtils.isBlank(AppConfig.OPENHUB_CLIENT_ID)) {
            loginBn.setVisibility(View.GONE);
        }
        // the browser answered after Android had closed OpenHub: the sign-in it belongs to
        // was saved (OAuthSession), so it can still be finished here
        if (savedInstanceState == null && getIntent() != null && getIntent().getData() != null
                && "openhub".equals(getIntent().getData().getScheme())) {
            mPresenter.handleOauth(getIntent());
        }
//        loginBn.setOnResultEndListener(new SubmitButton.OnResultEndListener() {
//            @Override
//            public void onResultEnd() {
//
//            }
//        });


//        passwordEt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//            @Override
//            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
//                if(actionId == EditorInfo.IME_ACTION_SEND
//                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
//                        event.getAction() == KeyEvent.ACTION_DOWN)){
//                    if(loginBn.isEnabled())
//                        ViewUtils.virtualClick(loginBn);
//                }
//                return false;
//            }
//        });

    }


    public void onOauthLoginClick(){
        AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getOAuth2Url());
    }

    public void onTokenLoginClick() {
        final EditText tokenEt = new EditText(getActivity());
        tokenEt.setSingleLine(true);
        tokenEt.setHint(R.string.token_hint);
        tokenEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout container = new FrameLayout(getActivity());
        int padding = getResources().getDimensionPixelSize(R.dimen.spacing_large);
        container.setPadding(padding, 0, padding, 0);
        container.addView(tokenEt);

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.token_login_title)
                .setMessage(R.string.token_login_message)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.create_token, null)
                .setPositiveButton(R.string.login, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getCreateTokenUrl()));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String token = tokenEt.getText().toString().trim();
                if (token.isEmpty()) {
                    tokenEt.setError(getString(R.string.token_empty));
                    return;
                }
                dialog.dismiss();
                loginBn.setEnabled(false);
                if (tokenLoginBn != null) tokenLoginBn.setEnabled(false);
                mPresenter.loginWithToken(token);
            });
        });
        dialog.show();
    }

//    @OnClick(R2.id.login_bn)
//    public void onLoginClick(){
//        if(loginCheck()){
//            loginBn.setEnabled(false);
//            mPresenter.basicLogin(userName, password);
//        }else{
//            loginBn.reset();
//        }
//    }

//    private boolean loginCheck(){
//        boolean valid = true;
//        userName = userNameEt.getText().toString();
//        password = passwordEt.getText().toString();
//        if(StringUtils.isBlank(userName)){
//            valid = false;
//            userNameLayout.setError(getString(R.string.user_name_warning));
//        }else{
//            userNameLayout.setErrorEnabled(false);
//        }
//        if(StringUtils.isBlank(password)){
//            valid = false;
//            passwordLayout.setError(getString(R.string.password_warning));
//        }else{
//            passwordLayout.setErrorEnabled(false);
//        }
//        return valid;
//    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        loginBn = root.findViewById(R.id.login_bn);
        tokenLoginBn = root.findViewById(R.id.token_login_bn);
        ViewUtils.setOnClick(root, R.id.login_bn, v -> onOauthLoginClick());
        ViewUtils.setOnClick(root, R.id.token_login_bn, v -> onTokenLoginClick());
    }
}
