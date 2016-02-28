package hack.blair.hackathon;

import com.qredo.device.android.conversation.Conversation;

import java.util.Date;

public class Contact {
    public Contact(String id, String displayName, Conversation conversation) {
        mId = id;
        mDisplayName = displayName;
        mConversation = conversation;
        mLocation = "Unknown";
        mTimestamp = new Date();
    }

    private String mId;
    private String mDisplayName;
    private Conversation mConversation;
    private double mLatitude;
    private double mLongitude;
    private String mLocation;
    private Date mTimestamp;

    public String getId() {
        return mId;
    }
    public String getDisplayName() {
        return mDisplayName;
    }
    public Conversation getConversation() {
        return mConversation;
    }
    public double getLatitude() { return mLatitude; }
    public double getLongitude() { return mLatitude; }
    public String getLocation() {
        return mLocation;
    }
    public Date getTimestamp() {
        return mTimestamp;
    }

    public void updateLocation(double latitude, double longitude, String location, Date timestamp) {
        mLatitude = latitude;
        mLongitude = longitude;
        mLocation = location;
        mTimestamp = timestamp;
    }
}
