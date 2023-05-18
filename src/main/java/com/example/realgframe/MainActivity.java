package com.example.realgframe;

import static android.speech.tts.TextToSpeech.SUCCESS;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

        Button btSendToDialogFlow;
        EditText etSendToDialogFlow;
        TextView tvResponseFromDialogFlow;
        SessionsClient sessionClient;
        SessionName sessionName;
        final static String UNIQUE_UUID = UUID.randomUUID().toString();

        ActivityResultLauncher<Intent> sttLauncher;
        Intent sttIntent;
        boolean ttsReady = false;
        TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        btSendToDialogFlow = findViewById(R.id.btSendToDialogFlow);
        etSendToDialogFlow = findViewById(R.id.etSendToDialogFlow);
        tvResponseFromDialogFlow = findViewById(R.id.tvResponseFromDialogFlow);

        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();

        tts = new TextToSpeech(this, status -> {
            if(status == SUCCESS) {
                ttsReady = true;
                //tts.setLanguage(new Locale("spa", "ES"));
            }
        });

        if (setupDialogFlowClient()) {
        btSendToDialogFlow.setOnClickListener(v -> {
            initSpeechToText();
            });
        } else {
            btSendToDialogFlow.setEnabled(false);
        }

    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String text = "";
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        text = r.get(0);
                    } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                        text = "Error";
                    }
                    sendMessageToBot(text);
                }
        );
    }

    private Intent getSttIntent() {
        Intent intencionSpeechToText = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_PROMPT, "Por favor, hable ahora");
        return intencionSpeechToText;
    }

    private void initSpeechToText() {
        sttLauncher.launch(sttIntent);
    }

    private void sendToDialogFlow() {
        String text = etSendToDialogFlow.getText().toString();
        etSendToDialogFlow.setText("");
        if(!text.isEmpty()) {
            sendMessageToBot(text);
        } else {
            Toast.makeText(this, "Introduce algo, ¿no?",Toast.LENGTH_SHORT).show();
        }
    }

    private boolean setupDialogFlowClient() {
        boolean value = true;
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.clients);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, UNIQUE_UUID);
        } catch (Exception e) {
            showMessage("\nexception in setupBot: " + e.getMessage() + "\n");
            value= false;
        }
        return value;
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            tvResponseFromDialogFlow.setText(message + "\n");
            if(ttsReady) {
                tts.speak(message, TextToSpeech.QUEUE_ADD, null, null);
            }
        });
    }

    private void sendMessageToBot(String message) {
        QueryInput input = QueryInput.newBuilder().setText(
                TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    DetectIntentRequest detectIntentRequest =
                            DetectIntentRequest.newBuilder()
                                    .setSession(sessionName.toString())
                                    .setQueryInput(input)
                                    .build();
                    DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
                    if(detectIntentResponse != null) {
                    //intent, action, sentiment
                        String action = detectIntentResponse.getQueryResult().getAction();
                        String intent = detectIntentResponse.getQueryResult().getIntent().toString();
                        String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();
                        String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                        if(!botReply.isEmpty()) {
                            showMessage(botReply + "\n");
                        } else {
                            showMessage("something went wrong\n");
                        }
                    } else {
                        showMessage("connection failed\n");
                    }
                } catch (Exception e) {
                    showMessage("\nexception in thread: " + e.getMessage() + "\n");
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }


}