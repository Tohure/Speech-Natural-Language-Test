package pe.tohure.speechnaturallanguagetest;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.speech.v1beta1.Speech;
import com.google.api.services.speech.v1beta1.SpeechRequestInitializer;
import com.google.api.services.speech.v1beta1.model.RecognitionAudio;
import com.google.api.services.speech.v1beta1.model.RecognitionConfig;
import com.google.api.services.speech.v1beta1.model.SpeechRecognitionResult;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeRequest;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeResponse;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private final String CLOUD_API_KEY = "ABCDEG1234567";
    private String base64EncodedData;
    private TextView speechToTextResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speechToTextResult = (TextView) findViewById(R.id.speech_to_text_result);
    }

    public void launchSelector(View view) {
        Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
        filePicker.setType("audio/flac");
        startActivityForResult(filePicker, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            final Uri soundUri = data.getData();
            convertBase64(soundUri);
        }
    }

    private void convertBase64(final Uri soundUri) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                InputStream stream;
                try {
                    stream = getContentResolver().openInputStream(soundUri);
                    assert stream != null;
                    byte[] audioData = IOUtils.toByteArray(stream);
                    stream.close();

                    base64EncodedData = Base64.encodeBase64String(audioData);
                    playSound(soundUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void playSound(Uri soundUri) throws IOException {
        MediaPlayer player = new MediaPlayer();
        player.setDataSource(MainActivity.this, soundUri);
        player.prepare();
        player.start();

        // Release the player
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mediaPlayer.release();
            }
        });

        launchSpeech(base64EncodedData);
    }

    private void launchSpeech(String base64EncodedData) throws IOException {
        Speech speechService =
                new Speech.Builder(AndroidHttp.newCompatibleTransport(),
                        new AndroidJsonFactory(), null).setSpeechRequestInitializer(
                        new SpeechRequestInitializer(CLOUD_API_KEY)).build();

        RecognitionConfig recognitionConfig = new RecognitionConfig();
        recognitionConfig.setLanguageCode("en-US");

        RecognitionAudio recognitionAudio = new RecognitionAudio();
        recognitionAudio.setContent(base64EncodedData);

        // Create request
        SyncRecognizeRequest request = new SyncRecognizeRequest();
        request.setConfig(recognitionConfig);
        request.setAudio(recognitionAudio);

        // Generate response
        SyncRecognizeResponse response;
        response = speechService.speech().syncrecognize(request).execute();


        // Extract transcript
        SpeechRecognitionResult result = response.getResults().get(0);
        final String transcript = result.getAlternatives().get(0).getTranscript();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                speechToTextResult.setText(transcript);
            }
        });
    }
}