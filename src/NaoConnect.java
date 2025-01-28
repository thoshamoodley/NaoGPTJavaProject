// Thosha Moodley
// Connect Nao to a python server which provides access to GPT
// Have a conversation between Nao and the user, with responses for Nao provided by GPT.
// Record user responses on Nao and save them
// locally for the python server to convert to text using OpenAI


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.time.Duration;

import com.aldebaran.qi.Tuple4;
import com.aldebaran.qi.helper.proxies.ALAudioRecorder;
import com.aldebaran.qi.helper.proxies.ALMemory;
import com.aldebaran.qi.helper.proxies.ALPhotoCapture;
import com.aldebaran.qi.helper.proxies.ALTextToSpeech;
import com.aldebaran.qi.helper.proxies.ALAutonomousLife;
import com.jcraft.jsch.*;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.Application;


// A class that controls dialog between Nao provided by a python GPT server, and a user speaking to Nao
public class NaoConnect {
    public static boolean isRoutineRunning = false;
    public static boolean stopConversation = false;
    public static void main(String[] args) throws Exception
    {
        try {

            // Define the URL to connect to the robot
            String robotUrl = "tcp://10.1.0.216:9559";
            // Create a new Aldebaran application
            Application application = new Application(args, robotUrl);
            // Start your Aldebaran application
            application.start();
            com.aldebaran.qi.Session session = application.session();
            new ALAutonomousLife(session).setState("disabled");

            // Create an ALTextToSpeech object and link it to your current session
            ALTextToSpeech tts = new ALTextToSpeech(application.session());

            // ask the user if they want to use vision or talk about a random topic
            tts.say("Hi there, do you want to talk about what I see or a random topic? touch the front of my head sensor to have a conversation or the middle head sensor for vision");
            System.out.println("Speech finished, subscribing to events");
            //take register for events from touch sensors on head, hand etc
            feel(session, application);

            System.out.println("Robot started, waiting in background");
            while(!Thread.interrupted()) {
                Thread.sleep(Duration.ofMillis(100));
            }

            System.out.println("Finished the program, stopping robot");
        } catch (Exception e) {
            e.printStackTrace();
        }



    }

    //get input for the middle and front head tactile sensors and hand sensor
    //and then trigger vision or conversation
    public static void feel(com.aldebaran.qi.Session session, Application application)
    {

        try
        {

            ALMemory memory = new ALMemory(session);
            memory.subscribeToEvent("FrontTactilTouched", o -> {
                System.out.println("Front of head was touched");
                if(!isRoutineRunning) {
                    isRoutineRunning = true;
                    runConversationInit(application); //run the routine for GPT to start a random conversation
                }
            });


            memory.subscribeToEvent("MiddleTactilTouched", o -> {
                System.out.println("Middle of head was touched");
                if(!isRoutineRunning) {
                    isRoutineRunning = true;
                    runConversationLook(application); // run the routine to use a camera image to start the conversation
                }

            });
            memory.subscribeToEvent("HandRightBackTouched", o -> {
                System.out.println("Right hand was touched");
                // end the conversation
                //if in the middle of a conversation, possibly recording
                if(isRoutineRunning) {
                    isRoutineRunning = false;
                    try {
                        ALAudioRecorder recorder = new ALAudioRecorder(application.session());
                        recorder.stopMicrophonesRecording();//ensure to stop recording
                        stopConversation = true;
                        ALTextToSpeech tts = new ALTextToSpeech(application.session());
                        tts.say("Thanks for talking to me! that's all for now");

                    }
                    catch(Exception e)
                    {
                        System.out.println(e.getMessage());
                    }
            }

            });



        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }

    }
    //take a picture with the camera and save it to local disk
    //contact python server and get back the start conversation text
    //then defer to a regular conversation
    public static void runConversationLook(Application application)
    {
        try {
            Session session = application.session();
            String convoText = look(session); //take a picture and get text back from openai
            runConversationGeneral(convoText, application); //run a normal conversation starting with the openai text

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        isRoutineRunning = false;
    }
    //start a conversation with a random topic from openai
    //then defer to a regular conversation
    public static void runConversationInit(Application application)
    {
        try {

            // Get the start of the conversation from the python server which connects to GPT
            String convoText = contactPythonServer("init");
            runConversationGeneral(convoText, application);

        }catch(Exception e)
        {
            System.out.println(e.getMessage());
        }
        isRoutineRunning = false;
    }

    //given the start of the conversation in convoText, start an endless loop of
    // recording speech from the user and speaking the response back to the user
    public static void runConversationGeneral(String convoText, Application application)
    {
        try {
            // Create an ALTextToSpeech object and link it to your current session
            ALTextToSpeech tts = new ALTextToSpeech(application.session());
            // Get the start of the conversation from the python server which connects to GPT

            // If a reply was received from GPT Nao should say the text and continue
            // the conversation
            if (!convoText.equals("")) {

                // Nao says the intro text provided by the GPT python server
                tts.say(convoText);

                // Continue the conversation until stopConversation
                while (true) {
                    if(!stopConversation) {
                        System.out.println("while loop ");

                        // Instruct Nao to record the response of the user
                        listen(application);
                        System.out.println("listen");

                        // Ssh to Nao and copy the recorded sound .wav file user response
                        // to a local location
                        getFile("Userinput.wav");
                        System.out.println("get file");

                        // Request with action 'convo' to python server to signal
                        // it to read wav file and retrieve reply from GPT
                        convoText = contactPythonServer("convo");
                        System.out.println("convotext " + convoText);

                        // Get Nao to speak out the response from GPT
                        tts.say(convoText);
                    }
                    else //stopConversation is true when the right arm is touched
                    {
                        stopConversation = false; //reset for the next conversation
                        break;//exit the while loop of the conversation
                    }
                }

            }
            // if no text was received from GPT, end the conversation
            else {

                // Nao tells us that something went wrong
                tts.say("Oops the response was empty");
            }
        }catch(Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
    // Record the vocal response from the user and store it on local disk
    public static void listen(Application application) throws Exception
    {
        try {
            ALAudioRecorder recorder = new ALAudioRecorder(application.session());
            recorder.stopMicrophonesRecording();
            System.out.println("start recording");
            recorder.startMicrophonesRecording("/home/nao/Userinput.wav", "wav", 16000, new Tuple4<>(0, 0, 1, 0));

            Thread.sleep(5000);
            recorder.stopMicrophonesRecording();
            System.out.println("stop recording");
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }

    }

   //take a picture from Nao's camera and save it to local disk
    public static String look(com.aldebaran.qi.Session session)
    {
        String convoText = "";
        try
        {
            System.out.println("Look function");
            ALPhotoCapture photocapture = new ALPhotoCapture(session);
            photocapture.setResolution(2);
            photocapture.setPictureFormat("jpg");
            Thread.sleep(5000); // pause so that the picture is not of you engaging Nao's sensor
            photocapture.takePictures(1, "/home/nao/", "NaoImage", true);
            getFile("NaoImage.jpg"); //get the file from Nao's disk and save it locally
            // Request with action 'vision' to python server to signal
            // it to send jpg file and retrieve reply from GPT
            convoText = contactPythonServer("vision");//trigger the python server to get the image and retrieve text from GPT
            System.out.println("Look convotext " + convoText);



        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        return convoText;
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
    public static void getFile(String filename) throws Exception
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
            System.out.println("Do not verify public key of Nao");
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            System.out.println("about to set config");
            session.setConfig(config);

            session.connect();
            System.out.println("tried to connect to session");

            // Location of file on Nao
            String command="scp -f /home/nao/" + filename;
            System.out.println("Command on Nao: "+command);
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
                var fileParts = filename.split("\\.");
                System.out.println("File name without extension: " + fileParts[0]);
                System.out.println("File extension: " + fileParts[1]);
                String newFileName = fileParts[0] + System.currentTimeMillis() + "." + fileParts[1];
                fos=new FileOutputStream(newFileName);
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
            ex.printStackTrace();
            System.out.println(ex.getMessage());
            System.out.println(ex.getStackTrace());
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


