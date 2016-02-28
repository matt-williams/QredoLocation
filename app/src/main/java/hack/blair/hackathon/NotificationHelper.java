package hack.blair.hackathon;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class NotificationHelper {
    private static final int ID = 1;
    private NotificationManager mNotificationManager;
    private State mState = State.OFFLINE;

    static enum State {
        OFFLINE,
        CONNECTING,
        ONLINE,
        TRACKING,
        COMMUNICATION_FAILURE,
        NO_CREDENTIALS
    };

    private NotificationCompat.Builder mConnectingBuilder;
    private NotificationCompat.Builder mOnlineBuilder;
    private NotificationCompat.Builder mTrackingBuilder;
    private NotificationCompat.Builder mCommunicationFailureBuilder;
    private NotificationCompat.Builder mNoCredentialsBuilder;

    public NotificationHelper(Context context) {
		mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingMainIntent = PendingIntent.getActivity(context, 0, mainIntent, 0);

        Intent loginIntent = new Intent(context, LoginActivity.class);
        PendingIntent pendingLoginIntent = PendingIntent.getActivity(context, 0, loginIntent, 0);

        mConnectingBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_connecting)
                .setContentTitle("Qredo Location Connecting...")
                .setContentText("Qredo Location is attempting to connect")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pendingMainIntent);

        mOnlineBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_online)
                .setContentTitle("Qredo Location Online")
                .setContentText("Qredo Location is online")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pendingMainIntent);

        mTrackingBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_online)
                .setContentTitle("Qredo Location Tracking")
                .setContentText("Qredo Location is tracking your location")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pendingMainIntent);

        mCommunicationFailureBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_offline)
                .setContentTitle("Qredo Location Connect Failure")
                .setContentText("Qredo Location failed to connect, and will retry")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pendingMainIntent);

        mNoCredentialsBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_offline                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 )
                .setContentTitle("Qredo Location Not Logged In")
                .setContentText("Qredo Location has no login details - enter them")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pendingLoginIntent);
    }

	public void notifyState(State state) {
        State oldState = mState;
        mState = state;
        if (state != oldState) {
            switch (state) {
                case OFFLINE:
                    mNotificationManager.cancel(ID);
                    break;
                case CONNECTING:
                    mNotificationManager.notify(ID, mConnectingBuilder.build());
                    break;
                case ONLINE:
                    mNotificationManager.notify(ID, mOnlineBuilder.build());
                    break;
                case TRACKING:
                    mNotificationManager.notify(ID, mTrackingBuilder.build());
                    break;
                case COMMUNICATION_FAILURE:
                    mNotificationManager.notify(ID, mCommunicationFailureBuilder.build());
                    break;
                case NO_CREDENTIALS:
                    mNotificationManager.notify(ID, mNoCredentialsBuilder.build());
                    break;
            }
        }
    }
}
