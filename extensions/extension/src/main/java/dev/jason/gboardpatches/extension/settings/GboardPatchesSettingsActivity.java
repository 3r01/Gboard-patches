package dev.jason.gboardpatches.extension.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.jason.gboardpatches.extension.BuildConfig;
import dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature;

public class GboardPatchesSettingsActivity extends Activity
        implements GboardPatchesSettingsContract.Host {
    private static final String TAG = "GboardPatches";
    private static final String ACTION_QS_TILE_PREFERENCES =
            "android.service.quicksettings.action.QS_TILE_PREFERENCES";
    private static final String EXTRA_OPEN_WEB_CLIPBOARD =
            "dev.jason.gboardpatches.extension.extra.OPEN_WEB_CLIPBOARD";
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final String TOOLBAR_TITLE_PATCHES = "Patches";
    private static final String HEADER_BADGE = "Gboard";
    private static final String HEADER_TITLE = "Patch settings";
    private static final String HEADER_SUMMARY =
            "Experimental settings for the current Gboard patch set.\n"
                    + "Long press any setting marked with [ i ] to view preview screenshots.";
    private static final String ERROR_HEADER_TITLE = "Feature unavailable";
    private static final String ERROR_HEADER_SUMMARY =
            "This settings page failed to load and was safely disabled.";
    private static final String ERROR_ROW_TITLE = "Unable to load feature";
    private static final String ERROR_ROW_SUMMARY =
            "The host app stayed alive. Reopen Gboard settings and try again.";
    private static final String FATAL_FALLBACK_TITLE = "Patches temporarily unavailable";
    private static final String FATAL_FALLBACK_SUMMARY =
            "This screen hit an internal error and was safely disabled. "
                    + "Gboard stays alive. Reopen settings and try again.";
    private static final String ABOUT_AUTHOR_TITLE = "Author";
    private static final String ABOUT_PATCH_VERSION_TITLE = "Patch Version";
    private static final String DIALOG_SAVE = "Save";
    private static final String DIALOG_CANCEL = "Cancel";
    private static final String DIALOG_CLOSE = "Close";
    private static final String DIALOG_ERROR_POSITIVE = "Enter a positive number.";
    private static final String DIALOG_ERROR_SAVE_FAILED = "Failed to save setting.";
    private static final String PREVIEW_HINT_PREFIX = "[ i ] ";

    private Palette palette;
    private LinearLayout toolbarView;
    private TextView toolbarTitleView;
    private TextView headerBadgeView;
    private TextView headerTitleView;
    private TextView headerSummaryView;
    private LinearLayout panelContainer;
    private ScrollView contentScrollView;
    private List<GboardPatchesSettingsContract.Feature> rootFeatures;
    private final Deque<GboardPatchesSettingsContract.Feature> featureNavigationStack =
            new ArrayDeque<GboardPatchesSettingsContract.Feature>();
    private Object backInvokedCallback;
    private boolean fatalFallbackShown;
    private final Handler screenRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable screenRefreshRunnable = this::refresh;

    public static Intent createWebClipboardSettingsIntent(Context context) {
        Intent intent = new Intent(context, GboardPatchesSettingsActivity.class);
        intent.putExtra(EXTRA_OPEN_WEB_CLIPBOARD, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(resolveActivityTheme());
        super.onCreate(savedInstanceState);
        try {
            fatalFallbackShown = false;
            palette = Palette.forConfiguration(getResources().getConfiguration());
            rootFeatures = GboardPatchesSettingsFeatureRegistry.features(this);
            configureWindow();
            View contentView = buildContentView();
            setContentView(contentView);
            installWindowInsetsHandling(contentView);
            registerBackCallback();
            openInitialFeatureFromIntentIfNeeded();
            renderCurrentScreenSafely();
        } catch (Throwable throwable) {
            showFatalFallbackScreen("Failed to initialize patches settings activity", throwable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCurrentScreenSafely();
    }

    @Override
    protected void onPause() {
        cancelScheduledScreenRefresh();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelScheduledScreenRefresh();
        unregisterBackCallback();
        super.onDestroy();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (navigateUpIfNeeded()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void refresh() {
        renderCurrentScreenSafely();
    }

    @Override
    public void openFeature(GboardPatchesSettingsContract.Feature feature) {
        if (feature == null) {
            return;
        }
        featureNavigationStack.addLast(feature);
        renderCurrentScreenSafely();
    }

    @Override
    public void showChoiceDialog(String title, String[] labels, String[] values,
            String currentValue, String customValue, Runnable customAction,
            GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        int checkedIndex = resolveCheckedIndex(values, currentValue);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checkedIndex, (dialogInterface, which) -> {
                    if (which < 0 || which >= values.length) {
                        return;
                    }
                    String selectedValue = values[which];
                    dialogInterface.dismiss();
                    runSafely("handle choice dialog selection", () -> {
                        if (customValue.equals(selectedValue)) {
                            customAction.run();
                        } else {
                            valueConsumer.accept(selectedValue);
                            renderCurrentScreenSafely();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        tintDialogButtons(dialog);
    }

    @Override
    public void showPositiveIntegerDialog(String title, String hint, int initialValue,
            GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setText(Integer.toString(initialValue));
        input.setSelectAllOnFocus(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(DIALOG_SAVE, null)
                .setNegativeButton(DIALOG_CANCEL, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                int value = parsePositiveInteger(input.getText().toString());
                if (value <= 0) {
                    input.setError(DIALOG_ERROR_POSITIVE);
                    return;
                }
                try {
                    consumer.accept(value);
                    dialog.dismiss();
                    renderCurrentScreenSafely();
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to persist positive integer setting", throwable);
                    input.setError(DIALOG_ERROR_SAVE_FAILED);
                }
            });
        });
        dialog.show();
        input.requestFocus();
        input.setSelection(input.getText().length());
    }

    @Override
    public void showTextInputDialog(String title, String hint, String initialValue,
            GboardPatchesSettingsContract.TextValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(hint);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(DIALOG_SAVE, null)
                .setNegativeButton(DIALOG_CANCEL, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    consumer.accept(input.getText() == null ? "" : input.getText().toString());
                    dialog.dismiss();
                    renderCurrentScreenSafely();
                } catch (IllegalArgumentException exception) {
                    input.setError(exception.getMessage());
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to persist text setting", throwable);
                    input.setError(DIALOG_ERROR_SAVE_FAILED);
                }
            });
        });
        dialog.show();
        input.requestFocus();
        input.setSelection(input.getText().length());
    }

    @Override
    public void showImageDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        String message = previewSpec.getMessage();
        if (message != null && !message.isEmpty()) {
            TextView messageView = new TextView(this);
            messageView.setText(message);
            messageView.setTextColor(palette.textSecondary);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            messageView.setLineSpacing(0f, 1.1f);
            container.addView(messageView);
        }

        List<GboardPatchesSettingsContract.PreviewImage> previewImages = previewSpec.getImages();
        boolean sideBySide = previewSpec.getLayout()
                == GboardPatchesSettingsContract.PreviewLayout.SIDE_BY_SIDE;
        if (!sideBySide || previewImages.size() <= 1) {
            boolean firstImage = true;
            for (GboardPatchesSettingsContract.PreviewImage previewImage : previewImages) {
                View imageCard = buildPreviewImageCard(previewSpec.getTitle(), previewImage, false);
                LinearLayout.LayoutParams imageCardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                imageCardParams.topMargin = firstImage ? dp(16) : dp(12);
                imageCard.setLayoutParams(imageCardParams);
                container.addView(imageCard);
                firstImage = false;
            }
        } else {
            for (int index = 0; index < previewImages.size(); index += 2) {
                LinearLayout previewRow = new LinearLayout(this);
                previewRow.setOrientation(LinearLayout.HORIZONTAL);
                previewRow.setBaselineAligned(false);
                LinearLayout.LayoutParams previewRowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                previewRowParams.topMargin = index == 0 ? dp(16) : dp(12);
                previewRow.setLayoutParams(previewRowParams);

                View leftCard = buildPreviewImageCard(
                        previewSpec.getTitle(),
                        previewImages.get(index),
                        true);
                LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
                leftParams.rightMargin = dp(6);
                previewRow.addView(leftCard, leftParams);

                if (index + 1 < previewImages.size()) {
                    View rightCard = buildPreviewImageCard(
                            previewSpec.getTitle(),
                            previewImages.get(index + 1),
                            true);
                    LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f);
                    rightParams.leftMargin = dp(6);
                    previewRow.addView(rightCard, rightParams);
                } else {
                    View spacer = new View(this);
                    previewRow.addView(spacer, new LinearLayout.LayoutParams(
                            0,
                            0,
                            1f));
                }

                container.addView(previewRow);
            }
        }
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(previewSpec.getTitle())
                .setView(scrollView)
                .setPositiveButton(DIALOG_CLOSE, null)
                .create();
        dialog.show();
        tintDialogButtons(dialog);
    }

    private View buildPreviewImageCard(String dialogTitle,
            GboardPatchesSettingsContract.PreviewImage previewImage, boolean compact) {
        LinearLayout imageCard = new LinearLayout(this);
        imageCard.setOrientation(LinearLayout.VERTICAL);
        imageCard.setGravity(Gravity.CENTER_HORIZONTAL);
        imageCard.setBackground(buildCardDrawable(
                palette.surfaceAlt,
                palette.surfaceStroke,
                dp(24)));
        imageCard.setPadding(
                compact ? dp(10) : dp(16),
                compact ? dp(12) : dp(16),
                compact ? dp(10) : dp(16),
                compact ? dp(12) : dp(16));

        Bitmap bitmap = decodePreviewBitmap(previewImage.getAssetPath());
        if (bitmap != null) {
            ImageView imageView = new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setMaxHeight(compact ? dp(220) : dp(420));
            imageView.setImageBitmap(bitmap);
            imageView.setContentDescription(dialogTitle);
            imageCard.addView(imageView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView errorView = new TextView(this);
            errorView.setText("Failed to load preview image.");
            errorView.setTextColor(palette.textSecondary);
            errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            imageCard.addView(errorView);
        }

        String caption = previewImage.getCaption();
        if (caption != null && !caption.isEmpty()) {
            TextView captionView = new TextView(this);
            captionView.setText(caption);
            captionView.setTextColor(palette.textPrimary);
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 12f : 13f);
            captionView.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = compact ? dp(8) : dp(12);
            captionView.setLayoutParams(captionParams);
            captionView.setGravity(Gravity.CENTER_HORIZONTAL);
            imageCard.addView(captionView);
        }

        return imageCard;
    }

    private Bitmap decodePreviewBitmap(String assetPath) {
        try (java.io.InputStream inputStream = getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to load preview asset: " + assetPath, throwable);
            return null;
        }
    }

    private void configureWindow() {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(palette.windowBackground);
            window.setNavigationBarColor(palette.windowBackground);
        }
    }

    private int resolveActivityTheme() {
        boolean nightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return nightMode
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar;
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.windowBackground);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildToolbar());

        contentScrollView = new ScrollView(this);
        contentScrollView.setClipToPadding(false);
        contentScrollView.setFillViewport(true);
        contentScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(16));
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeaderCard());
        content.addView(buildPanelCard());

        contentScrollView.addView(content);
        root.addView(contentScrollView);
        return root;
    }

    private View buildToolbar() {
        toolbarView = new LinearLayout(this);
        toolbarView.setOrientation(LinearLayout.HORIZONTAL);
        toolbarView.setGravity(Gravity.CENTER_VERTICAL);
        toolbarView.setBackgroundColor(palette.windowBackground);
        toolbarView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)));
        toolbarView.setPadding(dp(4), 0, dp(12), 0);

        View backButton = buildBackButton();

        toolbarTitleView = new TextView(this);
        toolbarTitleView.setTextColor(palette.textPrimary);
        toolbarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        toolbarTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        toolbarTitleView.setSingleLine(true);
        toolbarTitleView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        toolbarView.addView(backButton);
        toolbarView.addView(toolbarTitleView);
        return toolbarView;
    }

    private View buildBackButton() {
        View backButton = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(getResources().getDisplayMetrics().density * 2.15f);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float density = getResources().getDisplayMetrics().density;
                float iconSize = density * 24f;
                float left = (getWidth() - iconSize) / 2f;
                float top = (getHeight() - iconSize) / 2f;
                float scale = iconSize / 24f;

                paint.setColor(palette.textPrimary);
                path.reset();
                path.moveTo(left + (19.5f * scale), top + (12f * scale));
                path.lineTo(left + (7.8f * scale), top + (12f * scale));
                path.moveTo(left + (12.9f * scale), top + (7f * scale));
                path.lineTo(left + (7.8f * scale), top + (12f * scale));
                path.lineTo(left + (12.9f * scale), top + (17f * scale));
                canvas.drawPath(path, paint);
            }
        };
        backButton.setContentDescription("Navigate up");
        backButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        backButton.setBackground(buildRippleDrawable(dp(24)));
        backButton.setOnClickListener(view -> goBackOrFinish());
        return backButton;
    }

    private View buildHeaderCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(buildCardDrawable(palette.surfaceAlt, palette.surfaceStroke, dp(24)));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        headerBadgeView = new TextView(this);
        headerBadgeView.setAllCaps(true);
        headerBadgeView.setTextColor(palette.accent);
        headerBadgeView.setTypeface(Typeface.DEFAULT_BOLD);
        headerBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

        headerTitleView = new TextView(this);
        headerTitleView.setTextColor(palette.textPrimary);
        headerTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(8);
        headerTitleView.setLayoutParams(titleParams);

        headerSummaryView = new TextView(this);
        headerSummaryView.setTextColor(palette.textSecondary);
        headerSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        headerSummaryView.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(6);
        headerSummaryView.setLayoutParams(summaryParams);

        card.addView(headerBadgeView);
        card.addView(headerTitleView);
        card.addView(headerSummaryView);
        return card;
    }

    private View buildPanelCard() {
        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setBackground(buildCardDrawable(
                palette.surface,
                palette.surfaceStroke,
                dp(28)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.topMargin = dp(12);
        panelContainer.setLayoutParams(panelParams);
        panelContainer.setPadding(0, dp(6), 0, dp(6));
        return panelContainer;
    }

    private void goBackOrFinish() {
        if (navigateUpIfNeeded()) {
            return;
        }
        finish();
    }

    private void openExternalUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Ignore devices without a visible browser handler.
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to open external URL: " + url, throwable);
        }
    }

    private boolean navigateUpIfNeeded() {
        if (featureNavigationStack.isEmpty()) {
            return false;
        }
        featureNavigationStack.removeLast();
        renderCurrentScreenSafely();
        return true;
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[] { callbackClass },
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("hashCode".equals(method.getName())) {
                                return Integer.valueOf(System.identityHashCode(proxy));
                            }
                            if ("equals".equals(method.getName())) {
                                Object other = args != null && args.length > 0 ? args[0] : null;
                                return Boolean.valueOf(proxy == other);
                            }
                            if ("toString".equals(method.getName())) {
                                return "GboardPatchesOnBackInvokedCallbackProxy";
                            }
                        }
                        if ("onBackInvoked".equals(method.getName())) {
                            if (!navigateUpIfNeeded()) {
                                finish();
                            }
                        }
                        return null;
                    });
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher")
                    .invoke(this);
            if (dispatcher == null) {
                return;
            }
            int priorityDefault = dispatcherClass.getField("PRIORITY_DEFAULT").getInt(null);
            dispatcherClass.getMethod("registerOnBackInvokedCallback", int.class, callbackClass)
                    .invoke(dispatcher, priorityDefault, callback);
            backInvokedCallback = callback;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to register platform back callback", throwable);
            backInvokedCallback = null;
        }
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backInvokedCallback == null) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher")
                    .invoke(this);
            if (dispatcher != null) {
                dispatcherClass.getMethod("unregisterOnBackInvokedCallback", callbackClass)
                        .invoke(dispatcher, backInvokedCallback);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to unregister platform back callback", throwable);
        }
        backInvokedCallback = null;
    }

    private void installWindowInsetsHandling(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            updateToolbarInsets(resolveTopInset(insets));
            if (contentScrollView != null) {
                contentScrollView.setPadding(0, 0, 0, resolveBottomInset(insets));
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void updateToolbarInsets(int topInset) {
        if (toolbarView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = toolbarView.getLayoutParams();
        int targetHeight = dp(TOOLBAR_HEIGHT_DP) + topInset;
        if (layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight;
            toolbarView.setLayoutParams(layoutParams);
        }
        toolbarView.setPadding(dp(4), topInset, dp(12), 0);
    }

    private int resolveTopInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            return systemBarsInsets.top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private int resolveBottomInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            return systemBarsInsets.bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private void renderCurrentScreenSafely() {
        if (fatalFallbackShown) {
            return;
        }
        try {
            renderCurrentScreen();
        } catch (Throwable throwable) {
            showFatalFallbackScreen("Failed to render patches settings screen", throwable);
        }
    }

    private void renderCurrentScreen() {
        GboardPatchesSettingsContract.Screen screen;
        GboardPatchesSettingsContract.Feature activeFeature = currentFeature();
        try {
            screen = activeFeature == null
                    ? buildRootScreen()
                    : activeFeature.buildScreen(this);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render settings screen", throwable);
            screen = buildFeatureErrorScreen();
        }
        toolbarTitleView.setText(screen.getToolbarTitle());
        headerBadgeView.setText(screen.getHeaderBadge());
        headerTitleView.setText(screen.getHeaderTitle());
        headerSummaryView.setText(screen.getHeaderSummary());
        panelContainer.removeAllViews();
        for (GboardPatchesSettingsContract.Row row : screen.getRows()) {
            panelContainer.addView(createRowView(row));
        }
        scheduleScreenRefresh(screen.getRefreshIntervalMs());
    }

    private void scheduleScreenRefresh(long refreshIntervalMs) {
        cancelScheduledScreenRefresh();
        if (refreshIntervalMs <= 0L) {
            return;
        }
        screenRefreshHandler.postDelayed(screenRefreshRunnable, refreshIntervalMs);
    }

    private void cancelScheduledScreenRefresh() {
        screenRefreshHandler.removeCallbacks(screenRefreshRunnable);
    }

    private void showFatalFallbackScreen(String reason, Throwable throwable) {
        cancelScheduledScreenRefresh();
        fatalFallbackShown = true;
        Log.e(TAG, reason, throwable);
        if (palette == null) {
            palette = Palette.forConfiguration(getResources().getConfiguration());
        }
        toolbarView = null;
        toolbarTitleView = null;
        headerBadgeView = null;
        headerTitleView = null;
        headerSummaryView = null;
        panelContainer = null;
        contentScrollView = null;
        setContentView(buildFatalFallbackView());
    }

    private View buildFatalFallbackView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(palette.windowBackground);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView titleView = new TextView(this);
        titleView.setText(FATAL_FALLBACK_TITLE);
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView summaryView = new TextView(this);
        summaryView.setText(FATAL_FALLBACK_SUMMARY);
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        summaryView.setGravity(Gravity.CENTER_HORIZONTAL);
        summaryView.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(12);

        root.addView(titleView);
        root.addView(summaryView, summaryParams);
        return root;
    }

    private GboardPatchesSettingsContract.Screen buildRootScreen() {
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        for (GboardPatchesSettingsContract.Feature feature : rootFeatures) {
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    feature.getEntryTitle(),
                    feature.getEntrySummary(),
                    true,
                    () -> openFeature(feature)));
        }
        rows.add(new GboardPatchesSettingsContract.ActionRow(
                ABOUT_AUTHOR_TITLE,
                BuildConfig.PATCH_AUTHOR,
                true,
                false,
                () -> openExternalUrl(BuildConfig.PATCH_AUTHOR_URL)));
        rows.add(new GboardPatchesSettingsContract.ActionRow(
                ABOUT_PATCH_VERSION_TITLE,
                BuildConfig.PATCH_VERSION,
                true,
                false,
                () -> openExternalUrl(BuildConfig.PATCH_REPOSITORY_URL)));
        return new GboardPatchesSettingsContract.Screen(
                TOOLBAR_TITLE_PATCHES,
                HEADER_BADGE,
                HEADER_TITLE,
                HEADER_SUMMARY,
                rows);
    }

    private GboardPatchesSettingsContract.Screen buildFeatureErrorScreen() {
        GboardPatchesSettingsContract.Feature activeFeature = currentFeature();
        String toolbarTitle = activeFeature != null
                ? activeFeature.getEntryTitle()
                : TOOLBAR_TITLE_PATCHES;
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        rows.add(new GboardPatchesSettingsContract.InfoRow(
                ERROR_ROW_TITLE,
                ERROR_ROW_SUMMARY,
                false));
        return new GboardPatchesSettingsContract.Screen(
                toolbarTitle,
                HEADER_BADGE,
                ERROR_HEADER_TITLE,
                ERROR_HEADER_SUMMARY,
                rows);
    }

    private void openInitialFeatureFromIntentIfNeeded() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        boolean tilePreferencesIntent = ACTION_QS_TILE_PREFERENCES.equals(intent.getAction());
        boolean openWebClipboard = intent.getBooleanExtra(EXTRA_OPEN_WEB_CLIPBOARD, false);
        if (!tilePreferencesIntent && !openWebClipboard) {
            return;
        }

        GboardClipboardSettingsFeature clipboardFeature = findClipboardFeature();
        if (clipboardFeature == null || !clipboardFeature.isAvailable(this)) {
            return;
        }
        GboardPatchesSettingsContract.Feature webClipboardFeature =
                clipboardFeature.getWebClipboardFeature();
        if (webClipboardFeature == null || !webClipboardFeature.isAvailable(this)) {
            return;
        }

        featureNavigationStack.clear();
        featureNavigationStack.addLast(clipboardFeature);
        featureNavigationStack.addLast(webClipboardFeature);
    }

    private GboardClipboardSettingsFeature findClipboardFeature() {
        if (rootFeatures == null) {
            return null;
        }
        for (GboardPatchesSettingsContract.Feature feature : rootFeatures) {
            if (feature instanceof GboardClipboardSettingsFeature clipboardFeature) {
                return clipboardFeature;
            }
        }
        return null;
    }

    private GboardPatchesSettingsContract.Feature currentFeature() {
        return featureNavigationStack.peekLast();
    }

    private View createRowView(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            return createSwitchRow(switchRow);
        }
        if (row instanceof GboardPatchesSettingsContract.ActionRow actionRow) {
            return createActionRow(actionRow);
        }
        if (row instanceof GboardPatchesSettingsContract.InfoRow infoRow) {
            return createInfoRow(infoRow);
        }
        throw new IllegalArgumentException("Unsupported row type: " + row.getClass().getName());
    }

    private View createSwitchRow(GboardPatchesSettingsContract.SwitchRow rowModel) {
        LinearLayout row = buildBaseRow();

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textColumn.setLayoutParams(textParams);
        textColumn.setPadding(0, 0, dp(16), 0);

        TextView titleView = buildRowTitle();
        TextView summaryView = buildRowSummary();
        titleView.setText(rowModel.getTitle());
        summaryView.setText(displaySummary(rowModel));
        textColumn.addView(titleView);
        textColumn.addView(summaryView);

        Switch switchView = new Switch(this);
        switchView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        applySwitchTint(switchView);
        switchView.setChecked(rowModel.isChecked());
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rowModel.getToggleAction().accept(isChecked);
            renderCurrentScreen();
        });

        row.addView(textColumn);
        row.addView(switchView);
        bindSwitchRowState(row, titleView, summaryView, switchView, rowModel.isEnabled());
        bindPreviewLongPress(row, rowModel);
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("toggle switch row", switchView::toggle);
            }
        });
        return row;
    }

    private View createActionRow(GboardPatchesSettingsContract.ActionRow rowModel) {
        LinearLayout row = buildBaseRow();

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textColumn.setLayoutParams(textParams);
        textColumn.setPadding(0, 0, dp(16), 0);

        TextView titleView = buildRowTitle();
        TextView summaryView = buildRowSummary();
        titleView.setText(rowModel.getTitle());
        summaryView.setText(displaySummary(rowModel));
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        row.addView(textColumn);
        TextView chevronView = null;
        if (rowModel.shouldShowChevron()) {
            chevronView = new TextView(this);
            chevronView.setText("\u203a");
            chevronView.setTextColor(palette.textSecondary);
            chevronView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            chevronView.setTypeface(Typeface.DEFAULT_BOLD);
            chevronView.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(chevronView);
        } else {
            textColumn.setPadding(0, 0, 0, 0);
        }
        bindActionRowState(row, titleView, summaryView, chevronView, rowModel.isEnabled());
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("handle action row", rowModel.getAction());
            }
        });
        bindPreviewLongPress(row, rowModel);
        return row;
    }

    private View createInfoRow(GboardPatchesSettingsContract.InfoRow rowModel) {
        LinearLayout row = buildBaseRow();
        row.setClickable(false);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = buildRowTitle();
        TextView summaryView = buildRowSummary();
        titleView.setText(rowModel.getTitle());
        summaryView.setText(displaySummary(rowModel));
        textColumn.addView(titleView);
        textColumn.addView(summaryView);

        row.addView(textColumn);
        titleView.setTextColor(rowModel.isEnabled() ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(
                rowModel.isEnabled() ? palette.textSecondary : palette.textDisabled);
        row.setAlpha(rowModel.isEnabled() ? 1f : 0.92f);
        bindPreviewLongPress(row, rowModel);
        return row;
    }

    private String displaySummary(GboardPatchesSettingsContract.Row rowModel) {
        String summary = rowModel.getSummary();
        if (summary == null || summary.isEmpty() || rowModel.getPreviewSpec() == null) {
            return summary;
        }
        if (summary.startsWith(PREVIEW_HINT_PREFIX)
                || summary.startsWith("[i] ")
                || summary.startsWith("[i]")
                || summary.startsWith("[ i ]")) {
            return summary;
        }
        return PREVIEW_HINT_PREFIX + summary;
    }

    private void bindPreviewLongPress(View row, GboardPatchesSettingsContract.Row rowModel) {
        GboardPatchesSettingsContract.PreviewSpec previewSpec = rowModel.getPreviewSpec();
        if (previewSpec == null) {
            return;
        }
        row.setOnLongClickListener(view -> {
            if (!row.isEnabled()) {
                return false;
            }
            runSafely("show row preview", () -> showImageDialog(previewSpec));
            return true;
        });
    }

    private void bindSwitchRowState(LinearLayout row, TextView titleView, TextView summaryView,
            Switch switchView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        switchView.setEnabled(enabled);
        titleView.setTextColor(enabled ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        row.setAlpha(enabled ? 1f : 0.92f);
    }

    private void applySwitchTint(Switch switchView) {
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_enabled, android.R.attr.state_checked },
                new int[] { android.R.attr.state_enabled, -android.R.attr.state_checked },
                new int[] { -android.R.attr.state_enabled, android.R.attr.state_checked },
                new int[] { -android.R.attr.state_enabled, -android.R.attr.state_checked }
        };
        int[] thumbColors = new int[] {
                palette.accent,
                palette.textPrimary,
                blendAlpha(palette.accent, 0.5f),
                blendAlpha(palette.textDisabled, 0.85f)
        };
        int[] trackColors = new int[] {
                blendAlpha(palette.accent, 0.5f),
                blendAlpha(palette.textSecondary, 0.45f),
                blendAlpha(palette.accent, 0.28f),
                blendAlpha(palette.textDisabled, 0.3f)
        };
        switchView.setThumbTintList(new ColorStateList(states, thumbColors));
        switchView.setTrackTintList(new ColorStateList(states, trackColors));
    }

    private void runSafely(String operationName, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to " + operationName, throwable);
            renderCurrentScreen();
        }
    }

    private void bindActionRowState(LinearLayout row, TextView titleView, TextView summaryView,
            TextView chevronView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        titleView.setTextColor(enabled ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        if (chevronView != null) {
            chevronView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        }
        row.setAlpha(enabled ? 1f : 0.92f);
    }

    private LinearLayout buildBaseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackground(buildRippleDrawable(dp(18)));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView buildRowTitle() {
        TextView titleView = new TextView(this);
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return titleView;
    }

    private TextView buildRowSummary() {
        TextView summaryView = new TextView(this);
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        summaryView.setLayoutParams(params);
        return summaryView;
    }

    private void tintDialogButtons(AlertDialog dialog) {
        TextView positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(palette.accent);
        }
        TextView negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTextColor(palette.accent);
        }
        TextView neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutralButton != null) {
            neutralButton.setTextColor(palette.accent);
        }
    }

    private int parsePositiveInteger(String value) {
        if (value == null) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int resolveCheckedIndex(String[] values, String currentValue) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                return i;
            }
        }
        return -1;
    }

    private Drawable buildCardDrawable(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Drawable buildRippleDrawable(int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusDp);
        return new RippleDrawable(
                ColorStateList.valueOf(palette.pressedOverlay),
                null,
                mask);
    }

    private int blendAlpha(int color, float alphaFraction) {
        int alpha = Math.round(Color.alpha(color) * alphaFraction);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Palette {
        final int windowBackground;
        final int surface;
        final int surfaceAlt;
        final int surfaceStroke;
        final int textPrimary;
        final int textSecondary;
        final int textDisabled;
        final int accent;
        final int pressedOverlay;

        Palette(int windowBackground, int surface, int surfaceAlt, int surfaceStroke,
                int textPrimary, int textSecondary, int textDisabled, int accent,
                int pressedOverlay) {
            this.windowBackground = windowBackground;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.surfaceStroke = surfaceStroke;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textDisabled = textDisabled;
            this.accent = accent;
            this.pressedOverlay = pressedOverlay;
        }

        static Palette forConfiguration(Configuration configuration) {
            boolean nightMode = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            if (nightMode) {
                return new Palette(
                        Color.parseColor("#FF0F1217"),
                        Color.parseColor("#FF161B22"),
                        Color.parseColor("#FF121821"),
                        Color.parseColor("#1FFFFFFF"),
                        Color.parseColor("#FFF3F5F8"),
                        Color.parseColor("#FFAAB3C0"),
                        Color.parseColor("#FF657181"),
                        Color.parseColor("#FF8AB4F8"),
                        Color.parseColor("#1F8AB4F8"));
            }
            return new Palette(
                    Color.parseColor("#F5F7FB"),
                    Color.parseColor("#FFFFFFFF"),
                    Color.parseColor("#EEF3FB"),
                    Color.parseColor("#140F172A"),
                    Color.parseColor("#FF101828"),
                    Color.parseColor("#FF5F6B7A"),
                    Color.parseColor("#FFB6BFCC"),
                    Color.parseColor("#FF1A73E8"),
                    Color.parseColor("#141A73E8"));
        }
    }
}
