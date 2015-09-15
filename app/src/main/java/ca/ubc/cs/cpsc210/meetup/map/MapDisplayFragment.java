package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.Set;
import java.util.HashSet;

import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.exceptions.IllegalCourseTimeException;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.Schedule;

import org.json.JSONException;
import org.json.JSONArray;

import java.util.Random;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     */
    private String activeDay = "MWF";

    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     */
    private final String getStudentURL = "http://kramer.nss.cs.ubc.ca:8081/getStudent";

    /**
     * FourSquare URLs. 
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore?";
    private static String FOUR_SQUARE_CLIENT_ID = "VMQGAFSN1ELCYYJRLGSTTHOKJ3KFKBSPTRGFURL10K14WLHN";
    private static String FOUR_SQUARE_CLIENT_SECRET = "UON4BX0J1YZMMCCKQRHIKGGWYOKIKYA0BRRT3DQCHDYT2BK1";

    /*
     * MapQuest key
     */
    private static String MAPQUEST_KEY = "Fmjtd%7Cluu82luzl9%2C82%3Do5-94809z";


    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private List<Student> randomStudents = null;
    private Student me = null;
    private static int ME_ID = 44185149;

    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. 
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {


        //start with a clear map
        clearSchedules();

        activeDay = sharedPreferences.getString("dayOfWeek", "Error");
        SchedulePlot mySchedulePlot = new SchedulePlot(me.getSchedule().getSections(activeDay), me.getFirstName(), "#FF0000", R.drawable.ic_action_place);

        new GetRoutingForSchedule().execute(mySchedulePlot);
    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {

        new GetRandomSchedule().execute();
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudents = null;
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace() {

        String meetUpTime = sharedPreferences.getString("timeOfDay", "Error: Could not find timeOfDay");
        meetUpTime = (meetUpTime + ":00");
        String willingToWalkString = sharedPreferences.getString("placeDistance", "Error: Could not find placeDistance");
        int willingToWalk = Integer.parseInt(willingToWalkString);


        boolean amIFree = isThisStudentFreeAtThisTime(me, meetUpTime);
        boolean areRandomStudentsFree = true;

        for (Student randomStudent: randomStudents) {
            if (!isThisStudentFreeAtThisTime(randomStudent, meetUpTime)) {
                areRandomStudentsFree = false;
            }
        }

        if (amIFree && areRandomStudentsFree) {
            Schedule mySchedule = me.getSchedule();
            Building myLastBuilding = mySchedule.whereAmI(activeDay, meetUpTime);
            LatLon whereWasILast;
            whereWasILast = myLastBuilding.getLatLon();

            // Get a set of possible places to meet up near me
            Set<Place> placesNearMe = PlaceFactory.getInstance().findPlacesWithinDistance(whereWasILast, willingToWalk);

            // Create a set of possible meeting places and first load places near me into it
            Set<Place> placesToMeet = new HashSet<Place>(placesNearMe); // copy places near me into this set

            for (Student randomStudent : randomStudents) {
                Building randomStudentLastBuilding = randomStudent.getSchedule().whereAmI(activeDay, meetUpTime);
                LatLon whereWasRandomStudentLast;
                whereWasRandomStudentLast = randomStudentLastBuilding.getLatLon();
                // Get a set of possible places to meet up near this random student
                Set<Place> placesNearRandomStudent = PlaceFactory.getInstance().findPlacesWithinDistance(whereWasRandomStudentLast, willingToWalk);

                // Update placesToMeet to places common in both placesToMeet and placesNearRandomStudents
                placesToMeet.retainAll(placesNearRandomStudent);
            }

            for (Place p : placesToMeet) { //plot all the possible meeting places
                Building building = new Building(p.getName(), p.getLatLon());
                plotABuilding(building, p.getName(),
                        "Why not meet here at " + meetUpTime + "?" + "\n" +
                        "Address: " + p.getAddress() + "\n" +
                        "Price: " + p.getPrice() + "\n" +
                        "Tips: " + p.getText(),
                        R.drawable.ic_action_important);
            }

        } else if (!amIFree && areRandomStudentsFree) { // You are not free
                AlertDialog aDialog = createSimpleDialog("You are not available to meet up!");
                aDialog.show();
        } else if (amIFree && !areRandomStudentsFree) { // At least one randomStudent is not free
                Student busyStudent = randomStudents.get(0); // Initialize
                for (Student randomStudent : randomStudents) {
                    if (!isThisStudentFreeAtThisTime(randomStudent, meetUpTime)) {
                        busyStudent = randomStudent;
                    }
                }
                AlertDialog aDialog = createSimpleDialog(busyStudent.getFirstName() + " is not available to meet up!");
                aDialog.show();
        } else { // You and at least one random student are not free
                AlertDialog aDialog = createSimpleDialog("You and at least one friend are not available to meet up!");
                aDialog.show();
        }
    }

    public boolean isThisStudentFreeAtThisTime(Student student, String timeOfDay) {
        try {
        int time = parseTimeString(timeOfDay);
        Set<Section> sections = student.getSchedule().getSections(activeDay);
        Building lastBuilding = student.getSchedule().whereAmI(activeDay, timeOfDay);


        // if timeOfDay is before student's start time (student is not yet on campus), he/she is NOT FREE
        if (lastBuilding == null) {
            return false;
        }

        for (Section section : sections) {
                int start = parseTimeString(section.getCourseTime().getStartTime());
                int end = parseTimeString(section.getCourseTime().getEndTime());

                // Check to see if the student is in class at timeOfDay
                if (time >= start && time <= end) {
                    return false;
                } else {
                    // Check to see if the student is in class for the next 60 minutes
                    for (int i = 1; i < 60; i++) {
                        int newTime = time + i;
                        if (newTime >= start && newTime <= end) {
                            return false;
                        }
                    }
                }
            }

        } catch (IllegalCourseTimeException e){
            AlertDialog aDialog = createSimpleDialog("A CourseTime is missing a :");
            aDialog.show();
        }

        return true;

    }

    public int parseTimeString(String timeString) throws IllegalCourseTimeException {
        int indexOfColon = timeString.indexOf(":");
        if (indexOfColon <= 0)
            throw new IllegalCourseTimeException("Missing a : in the timeString.");
        int hours = Integer.parseInt(timeString.substring(0, indexOfColon));
        int minutes = Integer.parseInt(timeString.substring(indexOfColon + 1, timeString.length()));
        hours = hours * 100;

        int timeInt = hours + minutes;

        return timeInt;
    }

    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }

    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     *
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {

        String name = schedulePlot.getName();

        SortedSet<Section> sections = schedulePlot.getSections();

        for (Section s : sections) {
            String msg = s.getCourse().getCode() + " " + s.getCourse().getNumber() + " at " + s.getCourseTime().getStartTime();
            plotABuilding(s.getBuilding(), name, msg, R.drawable.ic_action_place);
        }


        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);

    }

    /**
     * Plot a building onto the map
     *
     * @param building      The building to put on the map
     * @param title         The title to put in the dialog box when the building is tapped on the map
     * @param msg           The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }


    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {

        studentManager = new StudentManager();

        //Add a student to the Student Manager
        studentManager.addStudent("Shyu", "Patience", 44185149);

        //Load my courses into my schedule via the Student Manager
        studentManager.addSectionToSchedule(44185149, "MATH", 103, "203");
        studentManager.addSectionToSchedule(44185149, "PHYS", 101, "203");
        studentManager.addSectionToSchedule(44185149, "EOSC", 118, "201");
        studentManager.addSectionToSchedule(44185149, "ENGL", 112, "208");

        //Set student to me field
        me = studentManager.get(44185149);

    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     *
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                    /**
                     * Display building description in dialog box when user taps stop.
                     *
                     * @param index
                     *            index of item tapped
                     * @param oi
                     *            the OverlayItem that was tapped
                     * @return true to indicate that tap event has been handled
                     */
                    @Override
                    public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                        new AlertDialog.Builder(getActivity())
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        if (selectedBuildingOnMap != null) {
                                            mapView.invalidate();
                                        }
                                    }
                                }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                                .show();

                        selectedBuildingOnMap = oi;
                        mapView.invalidate();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(int index, OverlayItem oi) {
                        // do nothing
                        return false;
                    }
                };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }

    /**
     * Create overlay with a specific color
     *
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

    // *********************** Asynchronous tasks
    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {

            if (randomStudents == null) {
                randomStudents = new ArrayList<Student>();
            }

            try {
                // Call web service for a random student (JSON String response)
                String randomStudentJSON = makeRoutingCall(getStudentURL);

                String randomStudentLastName = getRandomStudentLastName(randomStudentJSON);
                String randomStudentFirstName = getRandomStudentFirstName(randomStudentJSON);
                int randomStudentId = getRandomStudentId(randomStudentJSON);
                // Load student into the student manager
                studentManager.addStudent(randomStudentLastName, randomStudentFirstName, randomStudentId);

                loadRandomStudentSections(randomStudentJSON, randomStudentId);

                Student randomStudent = studentManager.get(randomStudentId);
                SortedSet<Section> randomStudentSections = randomStudent.getSchedule().getSections(activeDay);

                // Create a SchedulePlot object for the student (with a randomly generated color for line)
                SchedulePlot randomStudentSchedulePlot = new SchedulePlot(randomStudentSections, randomStudent.getFirstName(),
                        generateRandomColor(), R.drawable.ic_action_place);

                SortedSet<Section> sections = randomStudent.getSchedule().getSections(activeDay);

                //Get LatLons from each building in my schedule, convert to strings and load into list
                List<String> latlonstrings = new ArrayList<String>();
                for (Section s : sections) {
                    double lat = s.getBuilding().getLatLon().getLatitude();
                    double lon = s.getBuilding().getLatLon().getLongitude();
                    latlonstrings.add(String.valueOf(lat) + "," + String.valueOf(lon));
                }

                //Call MapQuest with each latlon pair, parse the response and load all into route
                List<GeoPoint> route = new ArrayList<GeoPoint>();
                for (int i = 0; i < latlonstrings.size() - 1; i++) {
                    String fromString = latlonstrings.get(i);
                    String toString = latlonstrings.get(i + 1);

                    String httpString = makeHttpString(MAPQUEST_KEY, fromString, toString);
                    String routingCallString = makeRoutingCall(httpString);
                    List<GeoPoint> parsedMapQuestResponse = parse(routingCallString);

                    route.addAll(parsedMapQuestResponse);
                }

                // Add random student to the list of randomStudents
                randomStudents.add(randomStudent);

                // Plot random student's route
                randomStudentSchedulePlot.setRoute(route);

                return randomStudentSchedulePlot;

            } catch (MalformedURLException e) {

            } catch (IOException e) {

            }

            return null;
        }

        private String generateRandomColor() {
            Random rand = new Random();

            int r = rand.nextInt(255);
            int g = rand.nextInt(255);
            int b = rand.nextInt(255);
            int randomColor = Color.rgb(r,g,b);

            return "#" + Integer.toHexString(randomColor).toUpperCase();
        }

        private List<GeoPoint> parse(String MapQuestReturnString) {

            List<GeoPoint> geoPoints = new ArrayList<GeoPoint>();

            try {
                JSONObject obj = new JSONObject(MapQuestReturnString);
                JSONArray jArray = obj.getJSONObject("route").
                        getJSONObject("shape").getJSONArray("shapePoints");

                for (int i = 0; i < jArray.length() - 1; i += 2) {

                    GeoPoint gPoint = new GeoPoint(jArray.getDouble(i), jArray.getDouble(i + 1));
                    geoPoints.add(gPoint);
                }

            } catch (JSONException e) {
                System.out.println("Caught JSONException in parse(String).");
            }

            return geoPoints;
        }

        private String makeHttpString(String key, String fromLatLon, String toLatLon) {
            return "http://open.mapquestapi.com/directions/v2/route?key=" +
                    MAPQUEST_KEY + "&outFormat=json&routeType=" +
                    "pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m&from=" +
                    fromLatLon + "&to=" + toLatLon + "&drivingStyle=2&highwayEfficiency=21.0";
        }

        private String getRandomStudentLastName(String randomStudentJSON) {
            String randomStudentLastName = "";
            try {
                JSONObject obj = new JSONObject(randomStudentJSON);
                randomStudentLastName = obj.getString("LastName");

            } catch (JSONException e) {
                System.out.println("Caught JSONException in getRandomStudentLastName.");
            }

            return randomStudentLastName;
        }

        private String getRandomStudentFirstName(String randomStudentJSON) {
            String randomStudentFirstName = "";
            try {
                JSONObject obj = new JSONObject(randomStudentJSON);
                randomStudentFirstName = obj.getString("FirstName");

            } catch (JSONException e) {
                System.out.println("Caught JSONException in getRandomStudentFirstName.");
            }

            return randomStudentFirstName;
        }

        private int getRandomStudentId(String randomStudentJSON) {
            int randomStudentId = 0;
            try {
                JSONObject obj = new JSONObject(randomStudentJSON);
                randomStudentId = obj.getInt("Id");

            } catch (JSONException e) {
                System.out.println("Caught JSONException in getRandomStudentId.");
            }

            return randomStudentId;
        }

        private void loadRandomStudentSections(String randomStudentJSON, int randomStudentId) {

            try {
                JSONObject obj = new JSONObject(randomStudentJSON);
                JSONArray arr = obj.getJSONArray("Sections");
                for (int i = 0; i < arr.length(); i++) {
                    String courseName = arr.getJSONObject(i).getString("CourseName");
                    int courseNumber = arr.getJSONObject(i).getInt("CourseNumber");
                    String sectionName = arr.getJSONObject(i).getString("SectionName");
                    studentManager.addSectionToSchedule(randomStudentId, courseName, courseNumber, sectionName);
                }
            } catch (JSONException e) {
                System.out.println("Caught a JSONException in loadRandomStudentSections.");
            }
        }

        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {

            List<GeoPoint> geoPoints = schedulePlot.getRoute();

            if (geoPoints.size() == 0) {
                AlertDialog aDialog = createSimpleDialog("There is no route to plot!");
                aDialog.show();
            }

            plotBuildings(schedulePlot);

            for (int i = 0; i < geoPoints.size() - 1; i++) {

                PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
                po.addPoint(geoPoints.get(i)); // one end of line
                po.addPoint(geoPoints.get(i + 1)); // second end of line
                scheduleOverlay.add(po);
                OverlayManager om = mapView.getOverlayManager();
                om.addAll(scheduleOverlay);
                mapView.invalidate();

            }
        }
    }


        /**
         * This asynchronous task is responsible for contacting the MapQuest web service
         * to retrieve a route between the buildings on the schedule and for plotting any
         * determined route on the map.
         */
        private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

            @Override
            protected void onPreExecute() {
            }

            @Override
            protected SchedulePlot doInBackground(SchedulePlot... params) {

                // The params[0] element contains the schedulePlot object
                SchedulePlot scheduleToPlot = params[0];

                SortedSet<Section> sections = me.getSchedule().getSections(activeDay);

                //Get LatLons from each building in my schedule, convert to strings and load into list
                List<String> latlonstrings = new ArrayList<String>();
                for (Section s : sections) {
                    double lat = s.getBuilding().getLatLon().getLatitude();
                    double lon = s.getBuilding().getLatLon().getLongitude();
                    latlonstrings.add(String.valueOf(lat) + "," + String.valueOf(lon));
                }

                //Call MapQuest with each latlon pair, parse the response and load all into route
                List<GeoPoint> route = new ArrayList<GeoPoint>();
                for (int i = 0; i < latlonstrings.size() - 1; i++) {
                    String fromString = latlonstrings.get(i);
                    String toString = latlonstrings.get(i + 1);
                    try {

                        String httpString = makeHttpString(MAPQUEST_KEY, fromString, toString);
                        String routingCallString = makeRoutingCall(httpString);
                        List<GeoPoint> parsedMapQuestResponse = parse(routingCallString);

                        route.addAll(parsedMapQuestResponse);

                    } catch (MalformedURLException e) {

                    } catch (IOException e) {

                    }
                }

                scheduleToPlot.setRoute(route);

                return scheduleToPlot;
            }

            private List<GeoPoint> parse(String MapQuestReturnString) {

                List<GeoPoint> geoPoints = new ArrayList<GeoPoint>();

                try {
                    JSONObject obj = new JSONObject(MapQuestReturnString);
                    JSONArray jArray = obj.getJSONObject("route").
                            getJSONObject("shape").getJSONArray("shapePoints");

                    for (int i = 0; i < jArray.length() - 1; i += 2) {

                        GeoPoint gPoint = new GeoPoint(jArray.getDouble(i), jArray.getDouble(i + 1));
                        geoPoints.add(gPoint);
                    }

                } catch (JSONException e) {
                    System.out.println("Caught JSONException in parse(String).");
                }

                return geoPoints;
            }

            private String makeHttpString(String key, String fromLatLon, String toLatLon) {
                return "http://open.mapquestapi.com/directions/v2/route?key=" +
                        MAPQUEST_KEY + "&outFormat=json&routeType=" +
                        "pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m&from=" +
                        fromLatLon + "&to=" + toLatLon + "&drivingStyle=2&highwayEfficiency=21.0";
            }

            private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
                URL url = new URL(httpRequest);
                HttpURLConnection client = (HttpURLConnection) url.openConnection();
                InputStream in = client.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String returnString = br.readLine();
                client.disconnect();
                return returnString;
            }

            @Override
            protected void onPostExecute(SchedulePlot schedulePlot) {

                List<GeoPoint> geoPoints = schedulePlot.getRoute();

                if (geoPoints.size() == 0) {
                    AlertDialog aDialog = createSimpleDialog("There is no route to plot!");
                    aDialog.show();
                }

                plotBuildings(schedulePlot);

                for (int i = 0; i < geoPoints.size() - 1; i++) {
                    PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
                    po.addPoint(geoPoints.get(i)); // one end of line
                    po.addPoint(geoPoints.get(i + 1)); // second end of line
                    scheduleOverlay.add(po);
                    OverlayManager om = mapView.getOverlayManager();
                    om.addAll(scheduleOverlay);
                    mapView.invalidate();
                }
            }
        }

        /**
         * This asynchronous task is responsible for contacting the FourSquare web service
         * to retrieve all places around UBC that have to do with food. It should load
         * any determined places into PlaceFactory and then display a dialog box of how it did
         */
        private class GetPlaces extends AsyncTask<Void, Void, String> {

            protected String doInBackground(Void... params) {

            //String fourSquareHttpsString = FOUR_SQUARE_URL + "?ll=" + UBC_MARTHA_PIPER_FOUNTAIN.getLatitude() + "," +
            //                                UBC_MARTHA_PIPER_FOUNTAIN.getLongitude() +
            //                                "&query=food&client_id=" + FOUR_SQUARE_CLIENT_ID
            //                                + "&client_secret=" + FOUR_SQUARE_CLIENT_SECRET + "&v=20150323";

            String fourSquareHttpsString = FOUR_SQUARE_URL + "client_id=" + FOUR_SQUARE_CLIENT_ID + "&client_secret=" + FOUR_SQUARE_CLIENT_SECRET +
                    "&v=20150401&ll=" +  UBC_MARTHA_PIPER_FOUNTAIN.getLatitude() + "," + UBC_MARTHA_PIPER_FOUNTAIN.getLongitude() +
                    "&query=" + sharedPreferences.getString("meetingPlace", "Error") +"&radius=2000";

            try {
                String fourSquareResponse = makeRoutingCall(fourSquareHttpsString);

                return fourSquareResponse;
                } catch (MalformedURLException e) {
                } catch (IOException e) {
                }

                return null;
            }

            private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
                URL url = new URL(httpRequest);
                HttpURLConnection client = (HttpURLConnection) url.openConnection();
                InputStream in = client.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String returnString = br.readLine();
                client.disconnect();
                return returnString;
            }

            protected void onPostExecute(String jSONOfPlaces) {

                int numberOfPlacesAdded = 0;

                try {
                    JSONObject obj = new JSONObject(jSONOfPlaces);
                    JSONArray items = obj.getJSONObject("response").getJSONArray("groups").
                                      getJSONObject(0).getJSONArray("items");

                    PlaceFactory places = PlaceFactory.getInstance();

                    for (int i = 0; i < items.length(); i++) {

                        String placeName = items.getJSONObject(i).getJSONObject("venue").getString("name");
                        double lat = items.getJSONObject(i).getJSONObject("venue").getJSONObject("location").getDouble("lat");
                        double lng = items.getJSONObject(i).getJSONObject("venue").getJSONObject("location").getDouble("lng");
                        LatLon latlon = new LatLon(lat, lng);

                        String address = items.getJSONObject(i).getJSONObject("venue").getJSONObject("location").getString("address");
                        String price = items.getJSONObject(i).getJSONObject("venue").getJSONObject("price").getString("message");
                        String text = items.getJSONObject(i).getJSONArray("tips").getJSONObject(0).getString("text");

                        places.add(new Place(placeName, latlon, address, price, text));
                        numberOfPlacesAdded = numberOfPlacesAdded + 1;

                    }

                } catch (JSONException e) {

                }

                AlertDialog aDialog = createSimpleDialog(numberOfPlacesAdded + " places were added to the PlaceFactory!");
                aDialog.show();
            }
        }

        /**
         * Initialize the CourseFactory with some courses.
         */
        private void initializeCourses() {
            CourseFactory courseFactory = CourseFactory.getInstance();

            Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

            Course cpsc210 = courseFactory.getCourse("CPSC", 210);
            Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
            cpsc210.addSection(aSection);
            aSection.setCourse(cpsc210);
            aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
            cpsc210.addSection(aSection);
            aSection.setCourse(cpsc210);
            aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
            cpsc210.addSection(aSection);
            aSection.setCourse(cpsc210);

            Course engl222 = courseFactory.getCourse("ENGL", 222);
            aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
            engl222.addSection(aSection);
            aSection.setCourse(engl222);

            Course scie220 = courseFactory.getCourse("SCIE", 220);
            aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
            scie220.addSection(aSection);
            aSection.setCourse(scie220);

            Course math200 = courseFactory.getCourse("MATH", 200);
            aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
            math200.addSection(aSection);
            aSection.setCourse(math200);

            Course fren102 = courseFactory.getCourse("FREN", 102);
            aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442, -123.252471)));
            fren102.addSection(aSection);
            aSection.setCourse(fren102);

            Course japn103 = courseFactory.getCourse("JAPN", 103);
            aSection = new Section("002", "MWF", "10:00", "10:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
            japn103.addSection(aSection);
            aSection.setCourse(japn103);

            Course scie113 = courseFactory.getCourse("SCIE", 113);
            aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
            scie113.addSection(aSection);
            aSection.setCourse(scie113);

            Course micb308 = courseFactory.getCourse("MICB", 308);
            aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704, -123.247536)));
            micb308.addSection(aSection);
            aSection.setCourse(micb308);

            Course math221 = courseFactory.getCourse("MATH", 221);
            aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
            math221.addSection(aSection);
            aSection.setCourse(math221);

            Course phys203 = courseFactory.getCourse("PHYS", 203);
            aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400, -123.252047)));
            phys203.addSection(aSection);
            aSection.setCourse(phys203);

            Course crwr209 = courseFactory.getCourse("CRWR", 209);
            aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039, -123.256129)));
            crwr209.addSection(aSection);
            aSection.setCourse(crwr209);

            Course fnh330 = courseFactory.getCourse("FNH", 330);
            aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167, -123.251157)));
            fnh330.addSection(aSection);
            aSection.setCourse(fnh330);

            Course cpsc499 = courseFactory.getCourse("CPSC", 430);
            aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632, -123.259334)));
            cpsc499.addSection(aSection);
            aSection.setCourse(cpsc499);

            Course chem250 = courseFactory.getCourse("CHEM", 250);
            aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
            chem250.addSection(aSection);
            aSection.setCourse(chem250);

            Course eosc222 = courseFactory.getCourse("EOSC", 222);
            aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
            eosc222.addSection(aSection);
            aSection.setCourse(eosc222);

            Course biol201 = courseFactory.getCourse("BIOL", 201);
            aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
            biol201.addSection(aSection);
            aSection.setCourse(biol201);

            Course cpsc121 = courseFactory.getCourse("CPSC", 121);
            aSection = new Section("203", "MWF", "14:00", "14:50", dmpBuilding);
            cpsc121.addSection(aSection);
            aSection = new Section("201", "TR", "08:00", "9:20", dmpBuilding);
            cpsc121.addSection(aSection);
            aSection.setCourse(cpsc121);

            // me
            Course math103 = courseFactory.getCourse("MATH", 103);
            aSection = new Section("203", "MWF", "08:00", "08:50", new Building("Chem", new LatLon(49.265901, -123.253078)));
            math103.addSection(aSection);
            aSection.setCourse(math103);

            Course phys101 = courseFactory.getCourse("PHYS", 101);
            aSection = new Section("203", "MWF", "16:00", "16:50", new Building("Forestry", new LatLon(49.260436, -123.248859)));
            phys101.addSection(aSection);
            aSection.setCourse(phys101);

            Course engl112 = courseFactory.getCourse("ENGL", 112);
            aSection = new Section("208", "TR", "08:00", "09:20", new Building("Henry Angus", new LatLon(49.265360, -123.253140)));
            engl112.addSection(aSection);
            aSection.setCourse(engl112);

            Course eosc118 = courseFactory.getCourse("EOSC", 118);
            aSection = new Section("201", "TR", "15:30", "16:50", new Building("Pharm", new LatLon(49.262285, -123.243422)));
            eosc118.addSection(aSection);
            aSection.setCourse(eosc118);
        }

    }
