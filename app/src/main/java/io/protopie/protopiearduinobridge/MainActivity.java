package io.protopie.protopiearduinobridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_CONSOLE = MainActivity.class.getCanonicalName() + ".CONSOLE";
    private static final String CONSOLE_STRING_KEY = "message";
    private static final int MAX_CONSOLE_LINES = 10;

    private TextView console;
    private List<String> consoleLines = new LinkedList<>();
    private StringBuilder consoleText = new StringBuilder();

    public static void sendToConsole(Context context, String message) {
        Intent intent = new Intent(ACTION_CONSOLE);
        intent.putExtra(CONSOLE_STRING_KEY, message);
        context.sendBroadcast(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        console = findViewById(R.id.console);
        console.setMovementMethod(new ScrollingMovementMethod());

        startService(new Intent(this, BridgeService.class));
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_CONSOLE));
    }

    private void writeToConsole(String message) {
        consoleLines.add(message);
        while (consoleLines.size() > MAX_CONSOLE_LINES) {
            consoleLines.remove(0);
        }

        consoleText.delete(0, consoleText.length());
        for (int i = 0; i < consoleLines.size(); i++) {
            if (i > 0) {
                consoleText.append("\n");
            }
            consoleText.append(consoleLines.get(i));
        }
        console.setText(consoleText);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CONSOLE.equals(intent.getAction())) {
                String message = intent.getStringExtra(CONSOLE_STRING_KEY);
                if (message != null) {
                    writeToConsole(message);
                }
            }
        }
    };
}
