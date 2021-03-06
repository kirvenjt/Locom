package edu.vanderbilt.locom;

import android.app.Activity;
import android.content.IntentSender;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;

    // TAG for logging
    private static final String TAG = "MainActivity";

    // Google play
    GoogleApiClient mGoogleApiClient;

    // store locomLocation, latitude, and longitude
    Location mLastLocation;
    String mLatitudeText = "";
    String mLongitudeText = "";
    static Double mLatitude = 0.0;
    static Double mLongitude = 0.0;

    static boolean hasLoggedIn = false;

    // store interest tags
    static InterestTags tags = new InterestTags(new String[]{});
    static UserSendable user = new UserSendable("unset", new LocomLocation(0.0, 0.0), tags);
    static Broadcasts broadcasts = new Broadcasts();


    // server to connect to (commented out - for testing with groupcast)
    protected static final int PORT = 2000; //20000;
    protected static final String SERVER = "52.11.228.217"; //"cs283.hopto.org";

    // networking
    Socket socket = null;
    BufferedReader in = null;
    static PrintWriter out = null;
    static boolean connected = false;


    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    static private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        // connect to Google Play and the server
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        connect();
    }

    /* Connects to Google Play and enables locomLocation services*/
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        switch (position) {
            // Home screen- view available messages
            case 0:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, HomeScreenFragment.newInstance(position + 1))
                        .commit();
                break;
            // Tags - choose tags that interest you and update them in server
            case 1:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, tagsFragment.newInstance(position + 1))
                        .commit();
                break;
            // Broadcast - send messages
            case 2:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, CreateBroadcastFragment.newInstance(position + 1))
                        .commit();
                break;
        }


    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        disconnect();
        super.onDestroy();
    }

    /********* Networking ******************************************************/
    /**
     * Connect to the server. This method is safe to call from the UI thread.
     */
    void connect() {

        new AsyncTask<Void, Void, String>() {

            String errorMsg = null;

            @Override
            protected String doInBackground(Void... args) {
                Log.i(TAG, "Connect task started");
                try {
                    connected = false;
                    socket = new Socket(InetAddress.getByName(SERVER), PORT);
                    Log.i(TAG, "Socket created");
                    in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream());

                    connected = true;
                    Log.i(TAG, "Input and output streams ready");

                } catch (UnknownHostException e1) {
                    errorMsg = e1.getMessage();
                } catch (IOException e1) {
                    errorMsg = e1.getMessage();
                    try {
                        if (out != null) {
                            out.close();
                        }
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
                Log.i(TAG, "Connect task finished");
                return errorMsg;
            }

            @Override
            protected void onPostExecute(String errorMsg) {
                if (errorMsg == null) {
                    Toast.makeText(getApplicationContext(),
                            "Connected to server", Toast.LENGTH_SHORT).show();

                    // start receiving
                    receive();

                    /////////////// for testing with groupcast server //////////////
                    //send("NAME,locom");
                    // send("msg,AMy," + mLatitudeText + ", " + mLongitudeText);

                } else {
                    Toast.makeText(getApplicationContext(),
                            "Could not Connect: \n Exiting", Toast.LENGTH_SHORT).show();
                    // can't connect: close the activity
                    finish();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Start receiving messages in gson format, always of type broadcast. Received lines are
     * handled in the onProgressUpdate method which runs on the UI thread.
     * This method is automatically called after a connection has been established.
     */

    void receive() {
        new AsyncTask<Void, String, Void>() {

            @Override
            protected Void doInBackground(Void... args) {
                Log.i(TAG, "Receive task started");
                try {
                    while (connected) {

                        String msg = in.readLine();

                        if (msg == null) { // other side closed the connection
                            break;
                        }
                        publishProgress(msg);
                    }

                } catch (UnknownHostException e1) {
                    Log.i(TAG, "UnknownHostException in receive task");
                } catch (IOException e1) {
                    Log.i(TAG, "IOException in receive task");
                } finally {
                    connected = false;
                    try {
                        if (out != null)
                            out.close();
                        if (socket != null)
                            socket.close();
                    } catch (IOException e) {
                        // catch
                    }
                }
                Log.i(TAG, "Receive task finished");
                return null;
            }

            @Override
            protected void onProgressUpdate(String... lines) {
                // the message received from the server is
                // guaranteed to be not null
                String msg = lines[0];

                // put received message in gson format - always type broadcast
                Gson gson = new Gson();
                System.out.println("raw message received: " + msg);

                LocomGSON message = gson.fromJson(msg, LocomGSON.class);
                if (message.type == null) {
                    message.type = "";
                } else if (message.type.equals("broadcast")) {
                    Toast.makeText(getApplicationContext(), "Incoming Broadcast: " +
                            message.broadcast.getTitle(), Toast.LENGTH_SHORT).show();
                    broadcasts.add(message.broadcast);
                } else {
                    Toast.makeText(getApplicationContext(), "Cannot handle incoming message",
                            Toast.LENGTH_SHORT).show();
                }
            }

        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    /**
     * Disconnect from the server
     */
    void disconnect() {
        new Thread() {
            @Override
            public void run() {
                if (connected) {
                    connected = false;
                }
                // make sure that we close the output, not the input
                if (out != null) {
                    out.flush();
                    out.close();
                }
                // in some rare cases, out can be null, so we need to close the socket itself
                if (socket != null)
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }

                Log.i(TAG, "Disconnect task finished");
            }
        }.start();
    }

    static boolean send(String msg) {
        if (!connected) {
            Log.i(TAG, "can't send: not connected");
            return false;
        }

        new AsyncTask<String, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(String... msg) {
                Log.i(TAG, "sending: " + msg[0]);
                out.println(msg[0]);
                return out.checkError();
            }

            @Override
            protected void onPostExecute(Boolean error) {
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, msg);

        return true;
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Connected to Google Play services!
        // get location
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        if (mLastLocation != null) {
            mLatitudeText = String.valueOf(mLastLocation.getLatitude());
            mLatitude = mLastLocation.getLatitude();
            mLongitudeText = String.valueOf(mLastLocation.getLongitude());
            mLongitude = mLastLocation.getLongitude();
        }

        Log.i(TAG, "Latitude " + mLatitudeText);
        Log.i(TAG, "Longitude " + mLongitudeText);

        // send server latitude and longitude - groupcast test
       // if (connected) {
            //send("NAME,locom");
            //
            // send("msg,AMy," + mLatitudeText + ", " + mLongitudeText);
        //}
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            //return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            mResolvingError = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }


    /********************************************************************************************
     * BROADCASTVIEW FRAGMENT- show details of selected broadcast message
     *******************************************************************************************/
    public static class BroadcastViewFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */

        // declare all UI elems
        TextView eventName;
        TextView eventNameEntry;
        TextView distance;
        TextView distanceEntry;
        TextView meters;
        TextView description;
        TextView descriptionEntry;
        TextView eventDate;
        TextView eventDateEntry;
        TextView time;
        TextView timeEntry;
        TextView tags;
        TextView tag1;
        Button backHome;
        View rootV;

        // selected broadcast
        Broadcast currentBroadcast;

        // to keep track of which broadcast chosen from list
        static int num = 0;

        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static BroadcastViewFragment newInstance(int listNum) {
            num = listNum;
            BroadcastViewFragment fragment = new BroadcastViewFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, listNum);
            fragment.setArguments(args);
            return fragment;
        }

        public BroadcastViewFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_broadcastview, container, false);
            rootV = rootView;

            // find all xml elements
            eventName = (TextView) rootView.findViewById(R.id.eventName);
            eventNameEntry = (TextView) rootView.findViewById(R.id.eventNameEntry);
            distance = (TextView) rootView.findViewById(R.id.distance);
            distanceEntry = (TextView) rootView.findViewById(R.id.distanceEntry);
            meters = (TextView) rootView.findViewById(R.id.meters);
            description = (TextView) rootView.findViewById(R.id.description);
            descriptionEntry = (TextView) rootView.findViewById(R.id.descriptionEntry);
            eventDate = (TextView) rootView.findViewById(R.id.eventDate);
            eventDateEntry = (TextView) rootView.findViewById(R.id.eventDateEntry);
            time = (TextView) rootView.findViewById(R.id.time);
            timeEntry = (TextView) rootView.findViewById(R.id.timeEntry);
            tags = (TextView) rootView.findViewById(R.id.tags);
            tag1 = (TextView) rootView.findViewById(R.id.tag1);
            backHome = (Button) rootView.findViewById(R.id.backHome);

            // save selected broadcast
            currentBroadcast = broadcasts.getList().get(num);

            // display broadcast parts:

            // entry title and description
            descriptionEntry.setText(currentBroadcast.getMessageBody());
            eventNameEntry.setText(currentBroadcast.getTitle());

            // date and time
            Date event = currentBroadcast.getEventDate();
            String eventString = event.toString();
            String[] parts = eventString.split(" ");
            timeEntry.setText(parts[3]);
            eventDateEntry.setText(parts[0] + " " + parts[1] + " " + parts[2]);

            // distance from event
            Float dist = user.distanceToBroadcast(currentBroadcast);
            distanceEntry.setText(dist.toString());

            // tags
            InterestTags itags = currentBroadcast.getTags();
            String[] tagsArr = new String[itags.getSize()];
            tagsArr = itags.getTags().toArray(tagsArr);
            String allTags = "";
            for (int i = 0; i < itags.getSize(); i++) {
                allTags += tagsArr[i] + "\n";
                tag1.setText(allTags);
            }

            // button to get back to homescreen
            backHome.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    fragmentManager.beginTransaction()
                            .replace(R.id.container, HomeScreenFragment.newInstance(1))
                            .commit();

                    mTitle = getString(R.string.title_section1);
                }
            });

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    /*******************************************************************************************
     * CREATEBROADCAST FRAGMENT- allow user to send a broadcast message by filling in
     *                            appropriate fields
     *******************************************************************************************/
    public static class CreateBroadcastFragment extends Fragment {

        // UI elements
        DatePicker dPicker;
        TimePicker tPicker;
        TextView titleEntry;
        TextView descriptionEntry;
        CheckBox foodCheck;
        CheckBox musicCheck;
        CheckBox puppiesCheck;
        CheckBox otherCheck;
        TextView otherEntry;
        Button sendBcast;
        TextView radius;
        CheckBox currentLocation;
        TextView longEntry;
        TextView latEntry;

        View rootV;

        // hold broadcast being created
        Broadcast currentBroadcast;

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static CreateBroadcastFragment newInstance(int sectionNumber) {
            CreateBroadcastFragment fragment = new CreateBroadcastFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public CreateBroadcastFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_createbroadcast, container, false);
            rootV = rootView;

            // find UI elements
            dPicker = (DatePicker) rootView.findViewById(R.id.datePicker);
            tPicker = (TimePicker) rootView.findViewById(R.id.timePicker);
            titleEntry = (TextView) rootView.findViewById(R.id.eventNameEntry);
            descriptionEntry = (TextView) rootView.findViewById(R.id.descriptionEntry);
            foodCheck = (CheckBox) rootView.findViewById(R.id.food);
            musicCheck = (CheckBox) rootView.findViewById(R.id.music);
            puppiesCheck = (CheckBox) rootView.findViewById(R.id.puppies);
            otherCheck = (CheckBox) rootView.findViewById(R.id.other);
            otherEntry = (TextView) rootView.findViewById(R.id.otherEntry);
            sendBcast = (Button) rootView.findViewById(R.id.sendBCast);
            radius = (TextView) rootView.findViewById(R.id.radius);
            longEntry = (TextView) rootView.findViewById(R.id.longitudeEntry);
            latEntry = (TextView) rootView.findViewById(R.id.latitudeEntry);
            currentLocation = (CheckBox) rootView.findViewById(R.id.currentLocation);

            // on click listener for sendBcast button (pushed after filling fields)
            sendBcast.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    // event name
                    String name = titleEntry.getText().toString();

                    // event description
                    String description = descriptionEntry.getText().toString();

                    // event date and time
                    int day = dPicker.getDayOfMonth();
                    int month = dPicker.getMonth();
                    int year = dPicker.getYear();
                    int hour = tPicker.getCurrentHour();
                    int minute = tPicker.getCurrentMinute();
                    int second = 0;

                    // now safe to push button to send
                    boolean properBroadcast = true;

                    // distance
                    String radStr = radius.getText().toString();
                    Log.i(TAG, "radStr=" + radStr);
                    int rad = 500;
                    if (radStr.equals("") || name.equals("") || description.equals("")) {
                        Toast.makeText(getActivity(),
                                "Incomplete Fields", Toast.LENGTH_SHORT).show();
                        properBroadcast = false;
                    }
                    if (radStr.equals("") || radStr == null || radius.getText().length() == 0) {
                        Toast.makeText(getActivity(),
                                "Incomplete Fields", Toast.LENGTH_SHORT).show();
                        properBroadcast = false;
                    } else {
                        rad = Integer.parseInt(radStr);
                    }

                    // location
                    double longitude = mLongitude;
                    double latitude = mLatitude;
                    if (!currentLocation.isChecked() && !longEntry.getText().toString().equals("")
                            && !latEntry.getText().toString().equals("")) {
                        longitude = Double.parseDouble(longEntry.getText().toString());
                        latitude = Double.parseDouble(latEntry.getText().toString());
                    }

                    // sent date and time
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, day, hour, minute, second);

                    Date eventDate = cal.getTime();

                    Date sentDate = new Date();

                    // create new locomlocation with long, lat
                    LocomLocation loc = new LocomLocation(longitude, latitude);

                    // tags
                    List<String> interestList = new ArrayList();
                    if (foodCheck.isChecked()) {
                        interestList.add("food");
                    }
                    if (puppiesCheck.isChecked()) {
                        interestList.add("puppies");
                    }
                    if (musicCheck.isChecked()) {
                        interestList.add("music");
                    }
                    if (otherCheck.isChecked()) {
                        if (!otherEntry.getText().toString().equals("")) {
                            interestList.add(otherEntry.getText().toString());
                        } else {
                            Toast.makeText(getActivity(),
                                    "No 'other' listed", Toast.LENGTH_SHORT).show();
                            properBroadcast = false;
                        }
                    }

                    if (!properBroadcast) {
                        Toast.makeText(getActivity(),
                                "Broadcast not sent", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // create new message of broadcast type
                    String[] strArr = new String[interestList.size()];
                    strArr = interestList.toArray(strArr);
                    InterestTags tags = new InterestTags(strArr);
                    currentBroadcast = new Broadcast(name, description, tags, loc, rad, sentDate, eventDate);

                    // send gson broadcast
                    Gson gson = new Gson();
                    LocomGSON LOCOMmsg = new LocomGSON("broadcast", currentBroadcast, null);
                    String jsonStr = gson.toJson(LOCOMmsg);
                    System.out.println(jsonStr);

                    send(jsonStr);

                    // go back to home screen once message sent
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    fragmentManager.beginTransaction()
                            .replace(R.id.container, HomeScreenFragment.newInstance(1))
                            .commit();
                    mTitle = getString(R.string.title_section1);
                }
            });

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    /********************************************************************************************
     * HOMESCREEN FRAGMENT- show all available messages, button to create broadcast
     *                      on first open, show login screen
     *******************************************************************************************/
    public static class HomeScreenFragment extends Fragment {

        // UI elements
        View rootV;
        Button bConnect;
        Button createBCast;
        EditText etName;
        View loginView;
        ListView bCastList;
        TextView longEntry;
        TextView latEntry;
        CheckBox demoLocation;
        View homeView;

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static HomeScreenFragment newInstance(int sectionNumber) {
            HomeScreenFragment fragment = new HomeScreenFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public HomeScreenFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_homescreen, container, false);

            // find UI elements
            bConnect = (Button) rootView.findViewById(R.id.connectButton);
            etName = (EditText) rootView.findViewById(R.id.etName);
            loginView = rootView.findViewById(R.id.loginView);
            homeView = rootView.findViewById(R.id.homeView);
            bCastList = (ListView) rootView.findViewById(R.id.bCastListView);
            createBCast = (Button) rootView.findViewById(R.id.createBCastButton);
            longEntry = (TextView) rootView.findViewById(R.id.longEntryLogin);
            latEntry = (TextView) rootView.findViewById(R.id.LatEntryLogin);
            demoLocation = (CheckBox) rootView.findViewById(R.id.custLoc);


            // on click listener for listview bCastList
            bCastList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v,
                                        int position, long id)

                {
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    fragmentManager.beginTransaction()
                            .replace(R.id.container, BroadcastViewFragment.newInstance(position))
                            .commit();

                }
            });


            // assign OnClickListener to user login
            bConnect.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    // user name
                    String name;
                    name = etName.getText().toString();

                    // can use custom location for demo purposes
                    if (demoLocation.isChecked() && !latEntry.getText().toString().equals("") &&
                            !longEntry.getText().toString().equals("")) {
                        mLatitude = Double.parseDouble(latEntry.getText().toString());
                        mLongitude = Double.parseDouble(longEntry.getText().toString());
                    }

                    // send gson connect message with username and lat/long (tags blank)
                    String[] tag = {};
                    InterestTags tags = new InterestTags(tag);
                    user = new UserSendable(name, new LocomLocation(mLongitude, mLatitude), tags);

                    Gson gson = new Gson();

                    LocomGSON LOCOMmsg = new LocomGSON("connect", null, user);

                    String jsonStr = gson.toJson(LOCOMmsg);

                    System.out.println(jsonStr);

                    send(jsonStr);

                    // now hide user login
                    hasLoggedIn = true;

                    hideUserLogin();
                    homeView.setVisibility(View.VISIBLE);
                }
            });

            // onClickListener for creatBCast button
            createBCast.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    fragmentManager.beginTransaction()
                            .replace(R.id.container, CreateBroadcastFragment.newInstance(1))
                            .commit();

                }
            });

            // bCastList view
            List<String> bCastArray = new ArrayList<>();

            // fill in list
            for (Iterator<Broadcast> i = broadcasts.getList().iterator(); i.hasNext(); ) {
                Log.i(TAG, "adding element to bcastlist");
                bCastArray.add(i.next().getTitle());
            }

            // show list
            bCastList.setAdapter(new ArrayAdapter<>(
                    getActivity(),
                    android.R.layout.simple_list_item_activated_1,
                    bCastArray));

            rootV = rootView;

            if (hasLoggedIn) {
                hideUserLogin();
            } else {
                homeView.setVisibility(View.GONE);
            }

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }

        public void hideUserLogin() {

            if (rootV.findViewById(R.id.loginView).getVisibility() != View.GONE) {
                rootV.findViewById(R.id.loginView).setVisibility(View.GONE);
            }

        }

    }

    /*********************************************************************************************
     * TAGS FRAGMENT- allow user to update chosen interest tags
     ********************************************************************************************/
    public static class tagsFragment extends Fragment {

        // UI elements
        CheckBox foodCheck;
        CheckBox musicCheck;
        CheckBox puppiesCheck;
        CheckBox otherCheck;
        TextView otherEntry;
        Button sendTags;

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static tagsFragment newInstance(int sectionNumber) {
            tagsFragment fragment = new tagsFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public tagsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_tags, container, false);

            // find UI elements
            foodCheck = (CheckBox) rootView.findViewById(R.id.food);
            musicCheck = (CheckBox) rootView.findViewById(R.id.music);
            puppiesCheck = (CheckBox) rootView.findViewById(R.id.puppies);
            otherCheck = (CheckBox) rootView.findViewById(R.id.other);
            otherEntry = (TextView) rootView.findViewById(R.id.otherEntry);
            sendTags = (Button) rootView.findViewById(R.id.sendTags);

            // onClickListener for sendTags button
            sendTags.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    List<String> interestList = new ArrayList();
                    if (foodCheck.isChecked()) {
                        interestList.add("food");
                    }
                    if (puppiesCheck.isChecked()) {
                        interestList.add("puppies");
                    }
                    if (musicCheck.isChecked()) {
                        interestList.add("music");
                    }
                    if (otherCheck.isChecked()) {
                        if (!otherEntry.getText().toString().equals("")) {
                            interestList.add(otherEntry.getText().toString());
                        } else {
                            Toast.makeText(getActivity(),
                                    "No 'other' listed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    // add tags to new InterestTags object
                    String[] strArr = new String[interestList.size()];
                    strArr = interestList.toArray(strArr);
                    InterestTags tags = new InterestTags(strArr);

                    // set new tags in user
                    user.setTags(tags);

                    // send gson of type update
                    Gson gson = new Gson();

                    LocomGSON LOCOMmsg = new LocomGSON("update", null, user);

                    String jsonStr = gson.toJson(LOCOMmsg);

                    System.out.println(jsonStr);

                    send(jsonStr);

                    // back to homescreen after sent
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    fragmentManager.beginTransaction()
                            .replace(R.id.container, HomeScreenFragment.newInstance(1))
                            .commit();
                    mTitle = getString(R.string.title_section1);
                }
            });

            return rootView;

        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }
}
