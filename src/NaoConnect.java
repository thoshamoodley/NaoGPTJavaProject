
import com.aldebaran.qi.Application;
import com.aldebaran.qi.helper.ALProxy;
import com.aldebaran.qi.helper.proxies.ALAudioRecorder;
import com.aldebaran.qi.helper.proxies.ALTextToSpeech;
import com.aldebaran.qi.helper.proxies.ALAudioDevice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NaoConnect {
    public static void main(String[] args) throws Exception
    {
        try {
            // Define the URL to connect to
            URL url = new URL("http://127.0.0.1:8001/?action=init");

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method
            connection.setRequestMethod("GET");

            // Set additional request headers if needed
            // connection.setRequestProperty("Content-Type", "application/json");

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response from the server
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print the response
            System.out.println("Response: " + response.toString());

            // Disconnect the connection
            connection.disconnect();

            String robotUrl = "tcp://10.1.0.215:9559";
            // Create a new application
            Application application = new Application(args, robotUrl);
            // Start your application
            application.start();
            // Create an ALTextToSpeech object and link it to your current session
            ALTextToSpeech tts = new ALTextToSpeech(application.session());
            // Make your robot say something
            tts.say(response.toString());


            application.stop();
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
    public static void listen(Application application) throws Exception
    {
        ALAudioRecorder recorder = new ALAudioRecorder(application.session());

        recorder.startMicrophonesRecording("/home/nao/test.wav", "wav", 16000,4);//what is this object supposted to be replaced by - the 4
        Thread.sleep(5000);
        recorder.stopMicrophonesRecording();
    }



}
