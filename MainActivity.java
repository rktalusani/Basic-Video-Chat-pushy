package com.tokbox.android.tutorials.basic_video_chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.Manifest;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.opentok.android.AudioDeviceManager;
import com.opentok.android.BaseAudioDevice;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Subscriber;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.SubscriberKit;
import com.tokbox.android.tutorials.basicvideochat.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;


import me.pushy.sdk.Pushy;
import me.pushy.sdk.util.exceptions.PushyException;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity
                            implements EasyPermissions.PermissionCallbacks,
                                        WebServiceCoordinator.Listener,
                                        Session.SessionListener,
                                        PublisherKit.PublisherListener,
                                        SubscriberKit.SubscriberListener{

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int RC_SETTINGS_SCREEN_PERM = 123;
    private static final int RC_VIDEO_APP_PERM = 124;

    // Suppressing this warning. mWebServiceCoordinator will get GarbageCollected if it is local.
    @SuppressWarnings("FieldCanBeLocal")
    private WebServiceCoordinator mWebServiceCoordinator;

    private Session mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;

    private Vibrator v;
    MediaPlayer player;

    private FrameLayout mPublisherViewContainer;
    private FrameLayout mSubscriberViewContainer;
    String localUser="";
    String remoteUser="";
    String token="";
    String currentSessionId="";
    String toktoken="";
    String apikey="";
    String archiveId="";

    boolean isSpeaker = false;
    private static final int SENDER=0;
    private static final int RECEIVER=1;
    private static final int CONNECTED=0;
    private static final int DISCONNECTED=1;
    private static final int RINGING=2;
    private int myRole=SENDER;
    private int state=DISCONNECTED;
    public static final String MY_PREFS_NAME = "MyPrefsFile";
    TextView timerView;
    long startTime = 0;
    String totalTime="";

    CountDownTimer callTimer=null;
    CountDownTimer incomingCallTimer=null;
    CountDownTimer acceptedCallTimer=null;
    AlertDialog ad=null;
    //runs without a timer by reposting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            timerView = (TextView)findViewById(R.id.timer);
            timerView.setText(String.format("%d:%02d", minutes, seconds));
            totalTime = String.format("%d:%02d", minutes, seconds);
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        Pushy.listen(this);
        setContentView(R.layout.activity_main);

        // initialize view objects from your layout
        mPublisherViewContainer = (FrameLayout)findViewById(R.id.publisher_container);
        mSubscriberViewContainer = (FrameLayout)findViewById(R.id.subscriber_container);

        requestPermissions();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                //your action
                try {
                    token = Pushy.register(getApplicationContext());
                    Log.e("PUSHYTOKEN",token);
                    SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
                    String restoredText = prefs.getString("username", null);
                    if (restoredText != null) {
                        localUser = restoredText;
                        sendTokenToServer();
                    }
                }catch(Exception e){
                    Log.e("Exception",e.getLocalizedMessage());
                }
            }
        };
        AsyncTask.execute(runnable);

        SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        String restoredText = prefs.getString("username", null);
        if (restoredText != null) {
            localUser = restoredText;
            Button cb = (Button)findViewById(R.id.callbtn);
            Button lb = (Button)findViewById(R.id.loginbtn);

            cb.setVisibility(View.VISIBLE);
            lb.setVisibility(View.INVISIBLE);

            TextView tv = (TextView)findViewById(R.id.who);
            tv.setText("Logged in as "+localUser);
        }

        final Button audiobtn = (Button)findViewById(R.id.audio);
        audiobtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Button b = (Button)findViewById(R.id.audio);
                if(!isSpeaker){
                    AudioDeviceManager.getAudioDevice().setOutputMode(
                            BaseAudioDevice.OutputMode.Handset);
                    AudioManager mAudioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                    //mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setSpeakerphoneOn(true);
                    isSpeaker = true;
                    b.setText("Turn speaker off");
                }
                else{
                    AudioDeviceManager.getAudioDevice().setOutputMode(
                            BaseAudioDevice.OutputMode.SpeakerPhone);
                    AudioManager mAudioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                   // mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setSpeakerphoneOn(false);
                    isSpeaker = false;
                    b.setText("Turn speaker on");
                }
            }
        });

        final Button callbtn = (Button)findViewById(R.id.callbtn);

        callbtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if(state==DISCONNECTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Who do you want to call?");

                    final EditText input = new EditText(MainActivity.this);
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    builder.setView(input);
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            remoteUser = input.getText().toString();
                            /* fetch his token from the server and send a push notification */
                            createSessionDetails();
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                }
                else if(state==CONNECTED){
                    mSession.disconnect();
                    mSession = null;

                    Button b = (Button)findViewById(R.id.callbtn);
                    b.setText("Call");
                    state = DISCONNECTED;
                    timerHandler.removeCallbacks(timerRunnable);
                    TextView tv = (TextView) findViewById(R.id.timer);
                    tv.setText("");
                    TextView ts = (TextView) findViewById(R.id.status);
                    ts.setText("");
                }
            }
        });

        Button acceptb = (Button)findViewById(R.id.accept);
        acceptb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                v.cancel();
                if(player != null) {
                    player.stop();
                    player.release();
                    player = null;
                }
                initializeSession(apikey, currentSessionId, toktoken);

                LinearLayout ll = (LinearLayout)findViewById(R.id.callpanel);
                ll.setVisibility(View.INVISIBLE);
                if(incomingCallTimer!=null)
                    incomingCallTimer.cancel();
                TextView ts = (TextView) findViewById(R.id.status);
                ts.setText("Connecting call");
                startAcceptedCallTimer(10000);
            }
        });

        Button rejectb = (Button)findViewById(R.id.reject);
        rejectb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if(v!=null) {
                    v.cancel();
                }
                if(player!=null) {
                    player.stop();
                    player.release();
                    player=null;
                }
                LinearLayout ll = (LinearLayout)findViewById(R.id.callpanel);
                ll.setVisibility(View.INVISIBLE);
                TextView ts = (TextView) findViewById(R.id.status);
                ts.setText("Rejected call");
                incomingCallTimer.cancel();
            }
        });

        Button more = (Button) findViewById(R.id.loginbtn);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Enter username");

// Set up the input
                final EditText input = new EditText(MainActivity.this);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT );
                builder.setView(input);

// Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        localUser = input.getText().toString();
                        sendTokenToServer();
                        Button cb = (Button)findViewById(R.id.callbtn);
                        Button lb = (Button)findViewById(R.id.loginbtn);
                        cb.setVisibility(View.VISIBLE);
                        lb.setVisibility(View.INVISIBLE);

                        SharedPreferences.Editor editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putString("username", localUser);
                        editor.apply();
                        TextView tv = (TextView)findViewById(R.id.who);
                        tv.setText("Logged in as "+localUser);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });


        if(getIntent() !=null && getIntent().getExtras() !=null && getIntent().getExtras().getString("session")!=null){
            Log.e("CALL","Calling receiveCall - "+getIntent().getExtras().getString("session"));
            receiveCall(this,getIntent());
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void startOutboundCallTimer(){

        callTimer = new CountDownTimer(40000, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                if(state == DISCONNECTED){
                    /*remote user has not accepted the call, cancel outgoing call */
                    mSession.disconnect();
                    state = DISCONNECTED;
                    TextView tv = (TextView)findViewById(R.id.status);
                    tv.setText("No Answer from "+remoteUser);
                    if(player != null) {
                        player.stop();
                        player.release();
                        player=null;
                    }
                    if(v!=null)
                        v.cancel();
                }
            }
        }.start();
    }

    private void startIncomingCallTimer(long millis){

        incomingCallTimer = new CountDownTimer(millis, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                if(state == DISCONNECTED){
                    /*local user has not accepted the call, cancel ringing */
                    if(player != null) {
                        player.stop();
                        player.release();
                        player=null;
                    }
                    if(v!=null)
                        v.cancel();
                    state = DISCONNECTED;
                    TextView tv = (TextView)findViewById(R.id.status);
                    tv.setText("Missed call from "+remoteUser);
                    LinearLayout ll = (LinearLayout)findViewById(R.id.callpanel);
                    ll.setVisibility(View.INVISIBLE);
                }
            }
        }.start();
    }

    private void startAcceptedCallTimer(long millis){

        acceptedCallTimer = new CountDownTimer(millis, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                if(state == DISCONNECTED){
                    /*remote user has not connected yet. probably dead? kill the session */
                    state = DISCONNECTED;
                    TextView tv = (TextView)findViewById(R.id.status);
                    tv.setText("Missed call from "+remoteUser);
                    LinearLayout ll = (LinearLayout)findViewById(R.id.callpanel);
                    ll.setVisibility(View.INVISIBLE);
                    mSession.disconnect();
                }
            }
        }.start();
    }

    private void sendSessionData(){
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://nexmoapac.hopto.me/opentok/savesession.php";
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        // response
                        Log.d("Response", response);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.d("Error.Response", error.getLocalizedMessage());
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<String, String>();
                Location l = getLocation();
                params.put("latitude", String.valueOf(l.getLatitude()));
                params.put("longitude", String.valueOf(l.getLongitude()));
                params.put("localuser", localUser);
                params.put("remoteuser", remoteUser);
                params.put("networktype", getNetworkType());
                params.put("sessionid", currentSessionId);
                params.put("totaltime", totalTime);

                return params;
            }
        };
        queue.add(postRequest);
    }
    private String sendPushForUser(String user){
        String sessionid = mSession.getSessionId();
        if(sessionid==null || sessionid ==""){
            Toast.makeText(this, "No Active session",Toast.LENGTH_LONG);
            return "";
        }

        player = MediaPlayer.create(MainActivity.this, R.raw.ringback_tone);
        player.setLooping(true);
        player.start();

        Calendar cal1 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long millis = cal1.getTimeInMillis();
        TextView status = (TextView)findViewById(R.id.status);
        status.setText("Calling "+remoteUser);
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://nexmoapac.hopto.me/opentok/sendpush.php?user="+user+"&session="+sessionid+"&caller="+localUser;

// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.d("VOLLEY","Response is: "+ response);
                        /* We have the token, send push notification now */
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("VOLLEY","That didn't work!");
                NetworkResponse networkResponse = error.networkResponse;
                if (networkResponse != null && networkResponse.statusCode == 404) {
                    // HTTP Status Code: 401 Unauthorized
                    Log.e("ERROR","404 error");
                }
            }
        });

// Add the request to the RequestQueue.
        queue.add(stringRequest);
        startOutboundCallTimer();
        return "";
    }
    private void sendTokenToServer(){

        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://nexmoapac.hopto.me/opentok/savetoken.php?user="+localUser+"&token="+token;
        Log.e("SENDTOKEN",url);

// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.d("VOLLEY","Response is: "+ response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("VOLLEY","That didn't work!");
                Toast.makeText(MainActivity.this,"Failed to register with server",Toast.LENGTH_LONG).show();
            }
        });

// Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private void startRecording(){

        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://nexmoapac.hopto.me/opentok/record.php?session="+currentSessionId+"&user="+localUser+"&remote="+remoteUser;

// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.d("VOLLEY","Response is: "+ response);
                        archiveId = response.trim();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("VOLLEY","That didn't work!"+ error.getMessage());
            }
        });

// Add the request to the RequestQueue.
        queue.add(stringRequest);
    }


    private void stopRecording(){

        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://nexmoapac.hopto.me/opentok/stoprecord.php?archiveId="+archiveId;

// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.d("VOLLEY","Response is: "+ response);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("VOLLEY","That didn't work!"+error.getMessage());
            }
        });

// Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private void createSessionDetails(){
        String sessionInfoUrlEndpoint="http://nexmoapac.hopto.me/opentok/token.php";
        RequestQueue reqQueue = Volley.newRequestQueue(this);
        reqQueue.add(new JsonObjectRequest(Request.Method.GET, sessionInfoUrlEndpoint,
                null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String apiKey = response.getString("apiKey");
                     currentSessionId = response.getString("sessionId");
                    String token = response.getString("token");

                    Log.i(LOG_TAG, "WebServiceCoordinator returned session information "+currentSessionId);
                    initializeSession(apiKey, currentSessionId, token);
                    if(myRole==SENDER)
                        sendPushForUser(remoteUser);

                } catch (JSONException e) {

                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }));
    }

    private String getNetworkType(){
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        boolean is3g = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                .isConnectedOrConnecting();
        boolean isWifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                .isConnectedOrConnecting();

        System.out.println(is3g + " net " + isWifi);

        if(isWifi)
            return "WiFi";
        if(is3g) {
            TelephonyManager mTelephonyManager = (TelephonyManager)
                    MainActivity.this.getSystemService(Context.TELEPHONY_SERVICE);
            int networkType = mTelephonyManager.getNetworkType();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                default:
                    return "Unknown";
            }
        }
        return "Unknown";
    }

    public Location getLocation() {
        try {
            LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                Location lastKnownLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocationGPS != null) {
                    return lastKnownLocationGPS;
                } else {
                    Location loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                    return loc;
                }
            } else {
                return null;
            }
        }catch (SecurityException e){
            Log.e("LocationError",e.getLocalizedMessage());
            return null;
        }
    }
    @Override
    protected void onStart() {
        super.onStart();

    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

                token = intent.getExtras().getString("token");
                sendTokenToServer();

        }
    };

    private void receiveCall(Context context, Intent intent){
        String session = intent.getExtras().getString("session");
        Log.e("INCOMINGCALL",session);
        String [] parts = session.split("\\|");
        currentSessionId = parts[0];
        toktoken = parts[1];
        apikey = parts[2];
        remoteUser = parts[3];
        long millis = Long.parseLong(parts[4]);



        TextView status = (TextView)findViewById(R.id.status);
        status.setText("Call from "+remoteUser);
        status.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);

        LinearLayout ll = (LinearLayout)findViewById(R.id.callpanel);
        ll.setVisibility(View.VISIBLE);
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        long[] pattern = {0, 100, 1000};
        v.vibrate(pattern, 0);
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        player = MediaPlayer.create(MainActivity.this, notification);
        player.setLooping(true);
        player.start();
        startIncomingCallTimer(40000);

    }
    private BroadcastReceiver mMessageReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            receiveCall(context,intent);

        }
    };
     /* Activity lifecycle methods */

    @Override
    protected void onPause() {

        Log.d(LOG_TAG, "onPause");

        super.onPause();

        if (mSession != null) {
            mSession.onPause();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver2);
    }

    @Override
    protected void onResume() {

        Log.d(LOG_TAG, "onResume");

        super.onResume();

        if (mSession != null) {
            mSession.onResume();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver((mMessageReceiver),
                new IntentFilter("MyData")
        );
        LocalBroadcastManager.getInstance(this).registerReceiver((mMessageReceiver2),
                new IntentFilter("IncomingCall")
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

        Log.d(LOG_TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size());
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

        Log.d(LOG_TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());

        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setTitle(getString(R.string.title_settings_dialog))
                    .setRationale(getString(R.string.rationale_ask_again))
                    .setPositiveButton(getString(R.string.setting))
                    .setNegativeButton(getString(R.string.cancel))
                    .setRequestCode(RC_SETTINGS_SCREEN_PERM)
                    .build()
                    .show();
        }
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private void requestPermissions() {

        String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.ACCESS_FINE_LOCATION };
        if (EasyPermissions.hasPermissions(this, perms)) {

        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.rationale_video_app), RC_VIDEO_APP_PERM, perms);
        }
    }

    private void initializeSession(String apiKey, String sessionId, String token) {
        Log.e("INITIALIZE","Session bilder-"+sessionId);
        mSession = new Session.Builder(this, apiKey, sessionId).build();
        mSession.setSessionListener(this);
        mSession.connect(token);
    }

    /* Web Service Coordinator delegate methods */

    @Override
    public void onSessionConnectionDataReady(String apiKey, String sessionId, String token) {

        Log.d(LOG_TAG, "ApiKey: "+apiKey + " SessionId: "+ sessionId + " Token: "+token);

        initializeSession(apiKey, sessionId, token);
    }

    @Override
    public void onWebServiceCoordinatorError(Exception error) {

        Log.e(LOG_TAG, "Web Service error: " + error.getMessage());
        Toast.makeText(this, "Web Service error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        finish();

    }

    /* Session Listener methods */

    @Override
    public void onConnected(Session session) {

        Log.d(LOG_TAG, "onConnected: Connected to session: "+session.getSessionId());

        // initialize Publisher and set this object to listen to Publisher events
        mPublisher = new Publisher.Builder(this).videoTrack(false).build();
        mPublisher.setPublisherListener(this);

        // set publisher video style to fill view
        mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
        mPublisherViewContainer.addView(mPublisher.getView());
        if (mPublisher.getView() instanceof GLSurfaceView) {
            ((GLSurfaceView) mPublisher.getView()).setZOrderOnTop(true);
        }
        mPublisher.getView().setVisibility(View.INVISIBLE);
        mSession.publish(mPublisher);


        startRecording();
    }

    @Override
    public void onDisconnected(Session session) {

        Log.d(LOG_TAG, "onDisconnected: Disconnected from session: "+session.getSessionId());

        Button b = (Button)findViewById(R.id.callbtn);
        b.setText("Call");
        TextView tv = (TextView) findViewById(R.id.timer);
        tv.setText("");
        TextView ts = (TextView) findViewById(R.id.status);
        Button audiob = (Button)findViewById(R.id.audio);
        audiob.setVisibility(View.INVISIBLE);

        if (mSubscriber != null) {
            mSubscriber = null;
            stopRecording();
        }
        timerHandler.removeCallbacks(timerRunnable);
        sendSessionData();
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        Log.d(LOG_TAG, "onStreamReceived: New Stream Received "+stream.getStreamId() + " in session: "+session.getSessionId());

        if (mSubscriber == null) {
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.setSubscribeToVideo(false);
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriber.setSubscriberListener(this);
            mSession.subscribe(mSubscriber);
            //mSubscriberViewContainer.addView(mSubscriber.getView());
            if(player!=null) {
                player.stop();
                player.release();
                player=null;
            }

            Button b = (Button)findViewById(R.id.callbtn);
            b.setText("Disconnect");

            TextView status = (TextView)findViewById(R.id.status);
            status.setText("In call with "+remoteUser);

            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
            state = CONNECTED;
            if(callTimer!=null)
                callTimer.cancel();
            if(acceptedCallTimer!=null){
                acceptedCallTimer.cancel();
            }

            Button audiobtn = (Button)findViewById(R.id.audio);
            AudioManager mAudioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.setMode(AudioManager.MODE_IN_CALL);
            if(mAudioManager.isSpeakerphoneOn()){
                isSpeaker = true;
                audiobtn.setText("Turn Speaker Off");
            }
            else{
                audiobtn.setText("Turn Speaker On");
                isSpeaker = false;
            }

            Button audiob = (Button)findViewById(R.id.audio);
            audiob.setVisibility(View.VISIBLE);

        }
        else {
            Log.e("ERROR","mSubscriber not null, so not creating new session");
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {

        Log.d(LOG_TAG, "onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());

        if (mSubscriber != null) {
            mSubscriber = null;
            //mSubscriberViewContainer.removeAllViews();
            state=DISCONNECTED;
            Button b = (Button)findViewById(R.id.callbtn);
            b.setText("Call");
            TextView tv = (TextView) findViewById(R.id.timer);
            tv.setText("");
            TextView ts = (TextView) findViewById(R.id.status);
            ts.setText("");
            timerHandler.removeCallbacks(timerRunnable);
            Button audiob = (Button)findViewById(R.id.audio);
            audiob.setVisibility(View.INVISIBLE);
            stopRecording();
            mSession.disconnect();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.e(LOG_TAG, "onError: "+ opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - "+opentokError.getMessage() + " in session: "+ session.getSessionId());

        showOpenTokError(opentokError);
    }

    /* Publisher Listener methods */

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        Log.d(LOG_TAG, "onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        Log.d(LOG_TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        Log.e(LOG_TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        Log.d(LOG_TAG, "onConnected: Subscriber connected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        Log.d(LOG_TAG, "onDisconnected: Subscriber disconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        Log.e(LOG_TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    private void showOpenTokError(OpentokError opentokError) {

        Toast.makeText(this, opentokError.getErrorDomain().name() +": " +opentokError.getMessage() + " Please, see the logcat.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void showConfigError(String alertTitle, final String errorMessage) {
        Log.e(LOG_TAG, "Error " + alertTitle + ": " + errorMessage);
        new AlertDialog.Builder(this)
                .setTitle(alertTitle)
                .setMessage(errorMessage)
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
