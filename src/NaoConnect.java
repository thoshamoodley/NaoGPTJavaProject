// Thosha Moodley
// Connect Nao to a python server which provides access to GPT
// Have a conversation between Nao and the user, with responses for Nao provided by GPT.
// Record user responses on Nao and save them
// locally for the python server to convert to text using OpenAI

import com.aldebaran.qi.Application;
import com.aldebaran.qi.helper.proxies.ALAudioRecorder;
import com.aldebaran.qi.helper.proxies.ALTextToSpeech;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import com.aldebaran.qi.Tuple4;
import com.jcraft.jsch.*;

import javax.print.attribute.standard.MediaSize;

// A class that controls dialog between Nao provided by a python GPT server, and a user speaking to Nao
public class NaoConnect {
    public static void main(String[] args) throws Exception
    {
        try {
            // Define the URL to connect to the robot
            String robotUrl = "tcp://10.1.0.215:9559";
            // Create a new Aldebaran application
            Application application = new Application(args, robotUrl);
            // Start your Aldebaran application
            application.start();
            // Create an ALTextToSpeech object and link it to your current session
            ALTextToSpeech tts = new ALTextToSpeech(application.session());

            // Get the start of the conversation from the python server which connects to GPT
            String convoText = contactPythonServer("init");

            // If a reply was received from GPT Nao should say the text and continue
            // the conversation
            if (!convoText.equals(""))
            {

                // Nao says the intro text provided by the GPT python server
                tts.say(convoText);

                // Continue the conversation for a few rounds
                for (int i = 0; i < 3; i++) {

                    System.out.println("for loop" + i);

                    // Instruct Nao to record the response of the user
                    listen(application);
                    System.out.println("listen");

                    // Ssh to Nao and copy the recorded sound .wav file user response
                    // to a local location
                    getFile();
                    System.out.println("get file");

                    // Request with action 'convo' to python server to signal
                    // it to read wav file and retrieve reply from GPT
                    convoText = contactPythonServer("convo");
                    System.out.println("convotext " + convoText);

                    // Get Nao to speak out the response from GPT
                    tts.say(convoText);
                }

            }
            // if no text was received from GPT, end the conversation
            else
            {

                // Nao tells us that something went wrong
                tts.say("Oops the response was empty");
            }

            application.stop();

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    // Record the response from the user and store it
    public static void listen(Application application) throws Exception
    {
        ALAudioRecorder recorder = new ALAudioRecorder(application.session());
        //recorder.stopMicrophonesRecording();
        recorder.startMicrophonesRecording("/home/nao/Userinput.wav", "wav", 16000, new Tuple4<>(0,0,1,0));
        Thread.sleep(7000);
        recorder.stopMicrophonesRecording();
    }

    /*
     Contacts the python server with action instruction which tells it whether to
     start a conversation or continue it
     Retrieves the response text from the server which is returned to be used as a
     conversational response to the end user of Nao
     String action: 'init' for starting a conversation, and 'convo' to signal to
     python server to look for a wav file from Nao with user conversation text
    */
    public static String contactPythonServer(String action)
    {
        // Initialise response text to be returned by the function
        String responseText = "";
        try {

            //the url for the python server
            URL url = new URL("http://127.0.0.1:8001/?action=" + action);

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method
            connection.setRequestMethod("GET");

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
            responseText = response.toString();
            // Disconnect the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseText;
    }

    /*
     Connects to Nao via SSH and saves the wav file with user response to local disk
    * */
    public static void getFile() throws Exception
    {
        //Get the file from nao
        try {
            System.out.println("about to start getting file");
            JSch jsch = new JSch();

            // Get environment variables with user, password, host
            String NAO_USER = System.getenv("NAOUSER");
            String NAO_PASSWORD = System.getenv("NAOPASSWORD");
            String NAO_HOST = System.getenv("HOST");
            System.out.println("USER " + NAO_USER + " PASSWORD " + NAO_PASSWORD + " HOST " + NAO_HOST);

            // Create session
            com.jcraft.jsch.Session session = jsch.getSession(NAO_USER, NAO_HOST, 22);
            System.out.println("about to set password");
            session.setPassword(NAO_PASSWORD);

            // Do not verify public key of Nao
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();
            System.out.println("tried to connect to session");

            // Location of file on Nao
            String command="scp -f /home/nao/Userinput.wav";
            Channel channel=session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out=channel.getOutputStream();
            InputStream in=channel.getInputStream();

            channel.connect();
            byte[] buf=new byte[1024];

            // send '\0'
            buf[0]=0; out.write(buf, 0, 1); out.flush();

            while(true){
                int c=checkAck(in);
                if(c!='C'){
                    break;
                }

                // read '0644 '
                in.read(buf, 0, 5);

                long filesize=0L;
                while(true){
                    if(in.read(buf, 0, 1)<0){
                        // error
                        break;
                    }
                    if(buf[0]==' ')break;
                    filesize=filesize*10L+(long)(buf[0]-'0');
                }

                String file=null;
                for(int i=0;;i++){
                    in.read(buf, i, 1);
                    if(buf[i]==(byte)0x0a){
                        file=new String(buf, 0, i);
                        break;
                    }
                }

                System.out.println("filesize="+filesize+", file="+file);

                // send '\0'
                buf[0]=0; out.write(buf, 0, 1); out.flush();

                // read a content of lfile
                FileOutputStream fos=null;
                fos=new FileOutputStream("NAO" + System.currentTimeMillis() + ".wav");
                int foo;
                while(true){
                    if(buf.length<filesize) foo=buf.length;
                    else foo=(int)filesize;
                    foo=in.read(buf, 0, foo);
                    if(foo<0){
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize-=foo;
                    if(filesize==0L) break;
                }
                fos.close();
                fos=null;

                if(checkAck(in)!=0){
                    System.exit(0);
                }

                // send '\0'
                buf[0]=0; out.write(buf, 0, 1); out.flush();
            }

            session.disconnect();


        }catch(Exception ex)
        {
            System.out.println(ex.getMessage());
        }
    }

    /*
    * Jsch code to check acknowledgement
    * */
    static int checkAck(InputStream in) throws IOException{
        int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;

        if(b==1 || b==2){
            StringBuffer sb=new StringBuffer();
            int c;
            do {
                c=in.read();
                sb.append((char)c);
            }
            while(c!='\n');
            if(b==1){ // error
                System.out.print(sb.toString());
            }
            if(b==2){ // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }



}


