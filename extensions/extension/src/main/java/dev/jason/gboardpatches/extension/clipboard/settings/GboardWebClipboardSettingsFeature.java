package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncWebPortal;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardEndpointResolver;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardPreferences;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardTileController;

public final class GboardWebClipboardSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardWebClipboard";
    private static final String ENTRY_TITLE = "Web Clipboard";
    private static final String ENTRY_SUMMARY =
            "Zero-install clipboard sharing hosted by this phone. Open the same LAN page on multiple devices to keep your phone and browsers in sync. Recommended: turn on Quick Settings Tile.";
    private static final String TITLE_ENABLE = "Enable Web Clipboard";
    private static final String TITLE_PORT = "Web server port";
    private static final String TITLE_PAIRING = "Require pairing code";
    private static final String TITLE_PAIRING_CODE = "Pairing code";
    private static final String TITLE_URLS = "Current URLs";
    private static final String TITLE_CLIENTS = "Connected clients";
    private static final String ERROR_PAIRING_CODE = "Enter a 4-digit code.";

    private final GboardPatchesSettingsContract.Feature connectedClientsFeature =
            new ConnectedClientsFeature();

    @Override
    public String getEntryTitle() {
        return ENTRY_TITLE;
    }

    @Override
    public String getEntrySummary() {
        return ENTRY_SUMMARY;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_WEB_CLIPBOARD);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        try {
            Context context = host.getContext();
            SharedPreferences preferences = WebClipboardPreferences.preferences(context);
            int port = WebClipboardPreferences.getPort(preferences);
            boolean enabled = WebClipboardPreferences.isEnabled(preferences);
            boolean pairingRequired = WebClipboardPreferences.isPairingRequired(preferences);
            String pairingCode = WebClipboardPreferences.getPairingCode(preferences);
            List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients =
                    ClipboardSyncService.getConnectedClientSnapshots();
            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();

            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    TITLE_ENABLE,
                    "",
                    true,
                    enabled,
                    value -> WebClipboardTileController.applyEnabled(context, value)));
            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    TITLE_PAIRING,
                    "Default is on. Desktop browsers must enter a simple 4-digit code before they can sync unless you turn this off.",
                    enabled,
                    pairingRequired,
                    value -> {
                        WebClipboardTileController.applyPairingRequired(context, value);
                        host.refresh();
                    }));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_PAIRING_CODE + ": " + pairingCode,
                    pairingRequired
                            ? "Desktop users enter this 4-digit code or open a URL that includes ?code="
                                    + pairingCode + "."
                            : "Pairing is off; this code is only used after you enable the switch.",
                    enabled,
                    () -> host.showTextInputDialog(
                            TITLE_PAIRING_CODE,
                            pairingCode,
                            pairingCode,
                            value -> {
                                String normalizedCode = normalizePairingCodeInput(value);
                                WebClipboardTileController.applyPairingCode(context, normalizedCode);
                                host.refresh();
                            })));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    "Regenerate pairing code",
                    "Create a new 4-digit code and restart the portal if it is running.",
                    enabled,
                    false,
                    () -> {
                        WebClipboardTileController.regeneratePairingCode(context);
                        host.refresh();
                    }));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_PORT + ": " + port,
                    "Choose the port used by the phone-hosted web UI.",
                    true,
                    () -> host.showPositiveIntegerDialog(
                            TITLE_PORT,
                            Integer.toString(WebClipboardPreferences.DEFAULT_PORT),
                            port,
                            value -> WebClipboardTileController.applyPort(context, value))));
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    TITLE_URLS,
                    String.join("\n", portalUrls(port)),
                    enabled));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_CLIENTS + ": " + clients.size(),
                    clientsHomeSummary(clients),
                    enabled,
                    () -> host.openFeature(connectedClientsFeature)));

            return new GboardPatchesSettingsContract.Screen(
                    ENTRY_TITLE,
                    "Gboard",
                    ENTRY_TITLE,
                    ENTRY_SUMMARY,
                    rows,
                    1_000L);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render Web Clipboard settings screen", throwable);
            return buildErrorScreen();
        }
    }

    private List<String> portalUrls(int port) {
        List<String> urls = new ArrayList<String>();
        for (String url : WebClipboardEndpointResolver.resolveUrls(port)) {
            urls.add(trimTrailingSlash(url));
        }
        return urls;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank() || !url.endsWith("/")) {
            return url;
        }
        return url.substring(0, url.length() - 1);
    }

    private String clientsHomeSummary(List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients) {
        if (clients.isEmpty()) {
            return "No browser clients are connected.";
        }
        return "Tap to view and kick browser clients.";
    }

    private String clientTitle(ClipboardSyncWebPortal.ConnectedClientSnapshot client) {
        String browser = client.browser == null || client.browser.isBlank()
                ? "Browser"
                : client.browser;
        return browser + " " + client.label;
    }

    private String clientDetail(ClipboardSyncWebPortal.ConnectedClientSnapshot client) {
        String agent = client.agent == null || client.agent.isBlank()
                ? "Unknown agent"
                : client.agent;
        return "IP: " + client.address + "\nClient agent: " + agent;
    }

    private static String normalizePairingCodeInput(String value) {
        String code = value == null ? "" : value.trim();
        if (!code.matches("\\d{4}")) {
            throw new IllegalArgumentException(ERROR_PAIRING_CODE);
        }
        return code;
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        rows.add(new GboardPatchesSettingsContract.InfoRow(
                "Web Clipboard settings unavailable",
                "The Web Clipboard settings screen failed to load. Reopen Gboard settings and try again.",
                false));
        return new GboardPatchesSettingsContract.Screen(
                ENTRY_TITLE,
                "Gboard",
                ENTRY_TITLE,
                ENTRY_SUMMARY,
                rows);
    }

    private final class ConnectedClientsFeature implements GboardPatchesSettingsContract.Feature {
        @Override
        public String getEntryTitle() {
            return TITLE_CLIENTS;
        }

        @Override
        public String getEntrySummary() {
            return "View connected Web Clipboard clients and kick browser sessions.";
        }

        @Override
        public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.Host host) {
            try {
                List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients =
                        ClipboardSyncService.getConnectedClientSnapshots();
                List<GboardPatchesSettingsContract.Row> rows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                if (clients.isEmpty()) {
                    rows.add(new GboardPatchesSettingsContract.InfoRow(
                            TITLE_CLIENTS + ": 0",
                            "No browser clients are connected.",
                            true));
                }
                for (ClipboardSyncWebPortal.ConnectedClientSnapshot client : clients) {
                    rows.add(new GboardPatchesSettingsContract.ActionRow(
                            "Kick " + clientTitle(client),
                            clientDetail(client),
                            true,
                            false,
                            () -> {
                                ClipboardSyncService.kickConnectedClient(client.id);
                                host.refresh();
                            }));
                }
                return new GboardPatchesSettingsContract.Screen(
                        TITLE_CLIENTS,
                        "Gboard",
                        TITLE_CLIENTS,
                        "Browser clients currently connected to the Web Clipboard portal.",
                        rows,
                        1_000L);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render connected Web Clipboard clients", throwable);
                return buildErrorScreen();
            }
        }
    }
}
