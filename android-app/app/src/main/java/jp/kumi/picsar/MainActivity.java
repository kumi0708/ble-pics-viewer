package jp.kumi.picsar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Map;

public final class MainActivity extends AppCompatActivity {
    private static final long STALE_AFTER_MS = 5_000L;

    private PreviewView previewView;
    private HudOverlayView hud;
    private Button scanButton;
    private BluetoothLeScanner scanner;
    private boolean scanning = false;
    private boolean pendingScan = false;
    private int packetCount = 0;
    private int demoIndex = 0;
    private long lastPacketAt = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable staleCheck = new Runnable() {
        @Override
        public void run() {
            if (scanning && lastPacketAt > 0 && SystemClock.elapsedRealtime() - lastPacketAt > STALE_AFTER_MS) {
                hud.showStale();
            }
            handler.postDelayed(this, 1_000L);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getScanRecord() == null) return;
            byte[] payload = result.getScanRecord().getManufacturerSpecificData(PicsParser.MANUFACTURER_ID);
            if (payload == null) return;
            try {
                PicsParser.PicsFrame next = PicsParser.parseType2(payload);
                if (next == null) return;
                packetCount++;
                lastPacketAt = SystemClock.elapsedRealtime();
                runOnUiThread(() -> hud.showFrame(next, "LIVE", packetCount));
            } catch (IllegalArgumentException ignored) {
                // A subsequent complete Type 2 advertisement can recover.
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                scanning = false;
                updateScanButton();
                hud.showError("BLEスキャン開始失敗 / CODE " + errorCode);
            });
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        configureImmersiveMode();
        buildInterface();
        ensureCameraPermission();
        handler.post(staleCheck);
    }

    private void configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 5, 4));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        hud = new HudOverlayView(this);
        root.addView(hud, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Button demoButton = createConsoleButton(getString(R.string.demo_step), false);
        demoButton.setOnClickListener(view -> showNextDemoFrame());
        controls.addView(demoButton, new LinearLayout.LayoutParams(dp(142), dp(58)));

        scanButton = createConsoleButton(getString(R.string.ble_start), true);
        scanButton.setOnClickListener(view -> toggleScan());
        controls.addView(scanButton, new LinearLayout.LayoutParams(dp(186), dp(58)));

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM
        );
        controlParams.setMargins(0, 0, dp(22), dp(10));
        root.addView(controls, controlParams);
        setContentView(root);
    }

    private Button createConsoleButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(primary ? 15 : 14);
        button.setTextColor(primary ? Color.rgb(3, 5, 4) : Color.rgb(233, 233, 223));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(buttonBackground(primary));
        return button;
    }

    private GradientDrawable buttonBackground(boolean primary) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(primary ? 3 : 1), Color.rgb(255, 138, 0));
        drawable.setColor(primary ? Color.rgb(255, 138, 0) : Color.argb(225, 3, 5, 4));
        return drawable;
    }

    private void ensureCameraPermission() {
        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void toggleScan() {
        if (scanning) {
            stopBleScan();
            return;
        }
        if (!hasBlePermissions()) {
            pendingScan = true;
            permissionLauncher.launch(blePermissions());
            return;
        }
        startBleScan();
    }

    private void onPermissions(Map<String, Boolean> result) {
        if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA)) || hasPermission(Manifest.permission.CAMERA)) {
            startCamera();
        } else if (result.containsKey(Manifest.permission.CAMERA)) {
            hud.showError("カメラ権限が必要です");
        }
        if (pendingScan) {
            pendingScan = false;
            if (hasBlePermissions()) startBleScan();
            else hud.showError("周辺デバイスの権限が必要です");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (Exception error) {
                hud.showError("カメラを開始できません");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("MissingPermission")
    private void startBleScan() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            hud.showError("Bluetooth LEに対応していません");
            return;
        }
        if (!adapter.isEnabled()) {
            hud.showError("Bluetoothを有効にしてください");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            hud.showError("BLEスキャナーを取得できません");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        packetCount = 0;
        lastPacketAt = 0L;
        scanning = true;
        updateScanButton();
        hud.showWaiting("0x01CE / PICS Type 2 受信待ち");
        scanner.startScan(new ArrayList<>(), settings, scanCallback);
    }

    @SuppressLint("MissingPermission")
    private void stopBleScan() {
        if (scanner != null && hasPermission(scanPermissionName())) scanner.stopScan(scanCallback);
        scanner = null;
        scanning = false;
        updateScanButton();
        hud.showWaiting("BLE受信を停止しました");
    }

    private void showNextDemoFrame() {
        if (scanning) stopBleScan();
        PicsParser.PicsFrame demo;
        if (demoIndex == 0) demo = PicsParser.demoFrame(0);
        else if (demoIndex == 1) demo = PicsParser.demoFrame(8);
        else if (demoIndex == 2) demo = PicsParser.demoAllRedFrame();
        else demo = PicsParser.demoFrame(12);
        demoIndex = (demoIndex + 1) % 4;
        packetCount++;
        hud.showFrame(demo, "DEMO", packetCount);
    }

    private void updateScanButton() {
        scanButton.setText(scanning ? R.string.ble_stop : R.string.ble_start);
        scanButton.setTextColor(Color.rgb(3, 5, 4));
        scanButton.setBackground(buttonBackground(true));
    }

    private boolean hasBlePermissions() {
        for (String permission : blePermissions()) {
            if (!hasPermission(permission)) return false;
        }
        return true;
    }

    private String[] blePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private String scanPermissionName() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? Manifest.permission.BLUETOOTH_SCAN
                : Manifest.permission.ACCESS_FINE_LOCATION;
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scanning) stopBleScan();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(staleCheck);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
