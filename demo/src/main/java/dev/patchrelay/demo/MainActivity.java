package dev.patchrelay.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import dev.patchrelay.Patch;
import dev.patchrelay.PatchRelay;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setText("PatchRelay: loading…");
        status.setTextSize(20);
        status.setPadding(32, 48, 32, 32);
        setContentView(status);
        new Thread(() -> {
            try {
                PatchRelay relay = new PatchRelay(this, "1", BuildConfig.PATCH_PUBLIC_KEY);
                Patch logic = relay.start((operation, input) -> "Original Java logic: hello " + input);
                String result = logic.invoke("greet", "PatchRelay");
                relay.markHealthy();
                runOnUiThread(() -> status.setText(result + "\nActive patch: " + relay.activeVersion()));
                String manifestUrl = getIntent().getStringExtra("manifestUrl");
                if (manifestUrl != null) {
                    boolean staged = relay.stage(manifestUrl);
                    runOnUiThread(() -> status.append(staged ? "\nUpdate verified. Close and reopen app." : "\nAlready current."));
                }
            } catch (Exception error) {
                android.util.Log.e("PatchRelayDemo", "Update failed", error);
                runOnUiThread(() -> status.append("\n" + error.getMessage()));
            }
        }, "patchrelay-demo").start();
    }
}
