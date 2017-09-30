package pe.tohure.speechnaturallanguagetest;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.language.v1.CloudNaturalLanguage;
import com.google.api.services.language.v1.CloudNaturalLanguageRequestInitializer;
import com.google.api.services.language.v1.model.AnnotateTextRequest;
import com.google.api.services.language.v1.model.AnnotateTextResponse;
import com.google.api.services.language.v1.model.Document;
import com.google.api.services.language.v1.model.Entity;
import com.google.api.services.language.v1.model.Features;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String CLOUD_API_KEY = "ABCDEG1234567";
    private String base64EncodedData;
    private TextView speechToTextResult, textToResult;
    private String language = "en-US";
    private CloudNaturalLanguage naturalLanguageService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speechToTextResult = (TextView) findViewById(R.id.speech_result);
        textToResult = (TextView) findViewById(R.id.speech_text);
        setUpLanguageApi();
    }

    private void setUpLanguageApi() {
        naturalLanguageService = new CloudNaturalLanguage.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(),
                null
        ).setCloudNaturalLanguageRequestInitializer(
                new CloudNaturalLanguageRequestInitializer(CLOUD_API_KEY)
        ).build();
    }

    public void launchSelector(View view) {
        Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
        filePicker.setType("audio/flac");
        startActivityForResult(filePicker, 1);
    }

    public void topicAnalizeText(View view) {
        makeDocument(textToResult.getText().toString(), "Analizando texto");
    }

    public void topicAnalize(View view) {
        makeDocument(speechToTextResult.getText().toString(), "Analizando texto del audio");
    }

    private void makeDocument(String textToAnalyce, String titleToast) {
        Toast toast = Toast.makeText(this, titleToast, Toast.LENGTH_LONG);
        toast.show();

        Document document = new Document();
        document.setType("PLAIN_TEXT");

        if (textToAnalyce.length() == 0) {
            toast.cancel();
            toast = Toast.makeText(this, "Texto vacío", Toast.LENGTH_SHORT);
            toast.show();
        } else {
            document.setContent(textToAnalyce);
            makeRequest(document);
        }
    }

    private void makeRequest(Document document) {
        Features features = new Features();
        features.setExtractEntities(true);
        features.setExtractDocumentSentiment(true);

        final AnnotateTextRequest request = new AnnotateTextRequest();
        request.setDocument(document);
        request.setFeatures(features);

        getAsyncResponse(request);
    }

    private void getAsyncResponse(final AnnotateTextRequest request) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    showResponseInDialog(naturalLanguageService.documents().annotateText(request).execute());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showResponseInDialog(AnnotateTextResponse response) {
        final List<Entity> entityList = response.getEntities();
        final float sentiment = response.getDocumentSentiment().getScore();
        final String sentiment_title;

        if (sentiment == 0) {
            sentiment_title = "Neutral";
        } else if (sentiment < 0) {
            sentiment_title = "Negativa";
        } else {
            sentiment_title = "Positiva";
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String entities = "";
                for (Entity entity : entityList) {
                    entities += "\n" + entity.getName().toUpperCase();
                }
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Opinión " + sentiment_title + " --> " + sentiment)
                        .setMessage("Este texto habla acerca de : \n\n" + entities)
                        .setNeutralButton("Ok", null)
                        .create();
                dialog.show();
            }
        });
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

        printTextUiThread("Transcribiendo audio . . .");

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
        Speech speechService = new Speech.Builder(AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(), null).setSpeechRequestInitializer(
                new SpeechRequestInitializer(CLOUD_API_KEY)).build();

        RecognitionConfig recognitionConfig = new RecognitionConfig();
        recognitionConfig.setLanguageCode(language);

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

        printTextUiThread(transcript);
    }

    private void printTextUiThread(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                speechToTextResult.setText(text);
            }
        });
    }

}