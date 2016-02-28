package hack.blair.hackathon;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Base64;
import android.util.Log;

import com.qredo.device.android.QredoClient;
import com.qredo.device.android.QredoConnection;
import com.qredo.device.android.conversation.Conversation;
import com.qredo.device.android.conversation.ConversationManager;
import com.qredo.device.android.conversation.ConversationRef;
import com.qredo.device.android.conversation.callback.ConversationCallback;
import com.qredo.device.android.conversation.callback.ConversationCreatedListener;
import com.qredo.device.android.conversationmessage.ConversationMessage;
import com.qredo.device.android.conversationmessage.ConversationMessageManager;
import com.qredo.device.android.conversationmessage.ConversationMessageRef;
import com.qredo.device.android.conversationmessage.callback.ConversationMessageCallback;
import com.qredo.device.android.conversationmessage.callback.ConversationMessageListener;
import com.qredo.device.android.rendezvous.Rendezvous;
import com.qredo.device.android.rendezvous.RendezvousManager;
import com.qredo.device.android.rendezvous.callback.RendezvousCallback;
import com.qredo.device.android.vault.VaultItem;
import com.qredo.device.android.vault.VaultItemHeader;
import com.qredo.device.android.vault.VaultItemRef;
import com.qredo.device.android.vault.VaultManager;
import com.qredo.device.android.vault.callback.VaultCallback;
import com.qredo.device.android.vault.callback.VaultItemCreatedListener;
import com.qredo.device.android.vault.callback.VaultItemHeaderMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class QredoService extends Service {
    private static final String TAG = "QredoService";
    private static final long CONNECTION_RETRY_INTERVAL = 30000;
    private static final String KEY_ID = "id";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_CONVERSATION = "conversation";
    private static final long LOCATION_MIN_TIME = 30000;
    private static final float LOCATION_MIN_DISTANCE = 25.0f;
    public final Binder mBinder = new LocalBinder();
    private NotificationHelper mNotificationHelper;
    private QredoConnection mConnection;
    private Handler mTryConnectHandler;
    private Timer mTimer;
    private QredoClient mClient;
    private List<Contact> mContacts;
    private Set<ContactsListener> mContactListeners = new HashSet<ContactsListener>();
    private String KEY_SENDER = "sender";
    private String KEY_LATITUDE = "latitude";
    private String KEY_LONGITUDE = "longitude";
    private String KEY_LOCATION = "location";
    private String KEY_TIMESTAMP = "timestamp";
    private LocationManager mLocationManager;
    private String mOurSenderId;
    private VaultItemCreatedListener mVaultItemListener = new VaultItemCreatedListener() {
        @Override
        public void onReceived(final VaultItemHeader item) {
            mClient.getConversationManager().listAll(new ConversationCallback<Set<Conversation>>() {
                @Override
                public void onSuccess(Set<Conversation> conversations) {
                    String conversationRef = item.getItemMetadata().get(KEY_CONVERSATION);
                    for (Conversation conversation : conversations) {
                        if (Base64.encodeToString(conversation.getConversationRef().getRef(), Base64.NO_PADDING | Base64.NO_WRAP).equals(conversationRef)) {
                            String id = item.getItemMetadata().get(KEY_ID);
                            String displayName = item.getItemMetadata().get(KEY_DISPLAY_NAME);
                            Contact contact = new Contact(id, displayName, conversation);
                            mContacts.add(contact);
                            mClient.getConversationMessageManager().addListener(conversation.getConversationRef(), new LocationMessageListener(contact));
                        }
                    }
                    notifyContactsListener(false, false);
                }

                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "conversationManager.listAll failed: " + s);
                }
            });
        }

        @Override
        public void onFailure(String s) {
            Log.e(TAG, "VaultItemCreatedListener.onFailure(" + s + ")");
        }
    };

    private void notifyContactsListener(boolean failure, boolean locationUpdate) {
        for (ContactsListener listener : mContactListeners) {
            if (!failure) {
                listener.onUpdate(mContacts);
            } else {
                listener.onFailure();
            }
        }
        if ((!failure) &&
                (!locationUpdate) && // Don't trigger if we're notify the listeners just because _we've_ got a location update from someone else
                (mContacts != null)) {
            if (mContacts.size() > 0) {
                Log.e(TAG, "Contact notification " + locationUpdate + " - broadcasting");
                mNotificationHelper.notifyState(NotificationHelper.State.TRACKING);
                startLocationListening();
            } else {
                mNotificationHelper.notifyState(NotificationHelper.State.ONLINE);
                stopLocationListening();
            }
        } else {
            stopLocationListening();
        }
    }

    private LocationListener mLocationListener;

    private void startLocationListening() {
        Log.e(TAG, "Starting Location Listening");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (mLocationListener == null) {
                mLocationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        sendLocation(location);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                        Log.e(TAG, "LocationListener.onStatusChanged " + provider + ": " + status);
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                        Log.e(TAG, "LocationListener.onProviderEnabled " + provider);

                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        Log.e(TAG, "LocationListener.onProviderDisabled " + provider);
                        sendLocation(null);
                    }
                };
                mLocationManager.requestSingleUpdate(new Criteria(), mLocationListener, mTryConnectHandler.getLooper());
                mLocationManager.requestLocationUpdates(LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE, new Criteria(), mLocationListener, mTryConnectHandler.getLooper());
            }
        }
    }

    private void stopLocationListening() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (mLocationListener != null) {
                mLocationManager.removeUpdates(mLocationListener);
                mLocationListener = null;
            }
        }
    }

    private void sendLocation(final Location location) {
        if (location != null) {
            GeocoderService.reverse(location.getLatitude(), location.getLongitude(), new GeocoderService.Callback() {
                @Override
                public void gotResult(String locationString) {
                    String latitudeString = Double.toString(location.getLatitude());
                    String longitudeString = Double.toString(location.getLongitude());
                    sendLocation(latitudeString, longitudeString, locationString);
                }
            }, mTryConnectHandler);
        } else {
            sendLocation(Double.toString(Double.NaN), Double.toString(Double.NaN), "Unknown");
        }
    }

    private void sendLocation(String latitudeString, String longitudeString, String locationString) {
        Date timestamp = new Date();
        String timestampString = Long.toString(timestamp.getTime());
        final ConversationMessageManager messageManager = mClient.getConversationMessageManager();
        for (Contact contact : mContacts) {
            ConversationMessage message = new ConversationMessage("");
            message.putMetadata(KEY_SENDER, mOurSenderId);
            message.putMetadata(KEY_LATITUDE, latitudeString);
            message.putMetadata(KEY_LONGITUDE, longitudeString);
            message.putMetadata(KEY_LOCATION, locationString);
            message.putMetadata(KEY_TIMESTAMP, timestampString);
            Log.e(TAG, "Sending message " + mOurSenderId + ", " + locationString + ", " + timestampString);
            messageManager.send(contact.getConversation().getConversationRef(), message, new ConversationMessageCallback<ConversationMessageRef>() {
                @Override
                public void onSuccess(ConversationMessageRef conversationMessageRef) {
                    Log.e(TAG, "ConversationMessageCallback(Send).onSuccess()");
                }

                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "ConversationMessageCallback(Send).onFailure(" + s + ")");
                }
            });
        }
    }

    public interface ContactsListener {
        public void onUpdate(List<Contact> contacts);
        public void onFailure();
    }

    public interface RendezvousConversationCallback {
        public void onGotRendezvous(Rendezvous rendezvous);
        public void onGotConversation(Conversation conversation);
        public void onFailure();
    }

    public class LocalBinder extends Binder {
        public boolean addContact(Contact contact) {
            return QredoService.this.addContact(contact);
        }

        public boolean removeContact(Contact contact) {
            return QredoService.this.removeContact(contact);
        }

        public boolean rendezvousConversation(RendezvousConversationCallback callback) {
            return QredoService.this.rendezvousConversation(callback);
        }

        public boolean acceptRendezvous(String tag, RendezvousConversationCallback callback) {
            return QredoService.this.acceptRendezvous(tag, callback);
        }

        public void listenForContacts(ContactsListener listener) {
            QredoService.this.listenForContacts(listener);
        }

        public void unlistenForContacts(ContactsListener listener) {
            QredoService.this.unlistenForContacts(listener);
        }
    }

    private boolean acceptRendezvous(String tag, final RendezvousConversationCallback callback) {
        if (mClient != null) {
            RendezvousManager rendezvousManager = mClient.getRendezvousManager();
            rendezvousManager.respond(tag, new RendezvousCallback<ConversationRef>() {
                @Override
                public void onSuccess(ConversationRef conversationRef) {
                    ConversationManager conversationManager = mClient.getConversationManager();
                    conversationManager.get(conversationRef, new ConversationCallback<Conversation>() {
                        @Override
                        public void onSuccess(Conversation conversation) {
                            callback.onGotConversation(conversation);
                        }

                        @Override
                        public void onFailure(String s) {
                            Log.e(TAG, "conversationManager.get() failed: " + s);
                        }
                    });
                }

                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "rendezvousManager.respond() failed: " + s);
                }
            });
            return true;
        } else {
            return false;
        }
    }

    private boolean rendezvousConversation(final RendezvousConversationCallback callback) {
        if (mClient != null) {
            RendezvousManager rendezvousManager = mClient.getRendezvousManager();
            String tag = UUID.randomUUID().toString();
            rendezvousManager.create(rendezvousManager.creationParamsBuilder(tag).build(), new RendezvousCallback<Rendezvous>() {
                @Override
                public void onSuccess(Rendezvous rendezvous) {
                    ConversationManager conversationManager = mClient.getConversationManager();
                    conversationManager.addListener(rendezvous.getRendezvousRef(), new ConversationCreatedListener() {
                        @Override
                        public void onReceived(Conversation conversation) {
                            callback.onGotConversation(conversation);
                        }

                        @Override
                        public void onFailure(String s) {
                            Log.e(TAG, "conversationManager.addListener() failed: " + s);
                            callback.onFailure();
                        }
                    });
                    callback.onGotRendezvous(rendezvous);
                }

                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "rendezvousManager.create() failed: " + s);
                    callback.onFailure();
                }
            });
            return true;
        } else {
            return false;
        }
    }

    private boolean addContact(Contact contact) {
        if (mClient != null) {
            VaultManager vaultManager = mClient.getVaultManager();
            byte[] conversationRef = contact.getConversation().getConversationRef().getRef();
            VaultItem item = new VaultItem(conversationRef);
            String id = contact.getId();
            String displayName = contact.getDisplayName();
            String conversation = Base64.encodeToString(conversationRef, Base64.NO_PADDING | Base64.NO_WRAP);
            item.putMetadata(KEY_ID, id);
            item.putMetadata(KEY_DISPLAY_NAME, displayName);
            item.putMetadata(KEY_CONVERSATION, conversation);
            Log.e(TAG, "Adding contact (" + id + ", " + displayName + ", " + conversation + ")");
            vaultManager.put(item, new VaultCallback<VaultItemRef>() {
                @Override
                public void onSuccess(VaultItemRef vaultItemRef) {
                    Log.e(TAG, "Successfully added item");
                }

                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "Failed to add item: " + s);
                }
            });
            return true;
        } else {
            return false;
        }
    }

    private boolean removeContact(Contact contact) {
        if (mClient != null) {
            final String displayName = contact.getDisplayName();
            final VaultManager vaultManager = mClient.getVaultManager();
            vaultManager.findHeaders(new VaultItemHeaderMatcher() {
                                         @Override
                                         public boolean match(VaultItemHeader item) {
                                             return displayName.equals(item.getItemMetadata().get(KEY_DISPLAY_NAME));
                                         }
                                     },
                    new VaultCallback<Set<VaultItemHeader>>() {
                        @Override
                        public void onSuccess(Set<VaultItemHeader> vaultItemHeaders) {
                            for (VaultItemHeader header : vaultItemHeaders) {
                                Log.e(TAG, "Deleting " + header.getRef().toString());
                                vaultManager.delete(header.getRef(), new VaultCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean aBoolean) {
                                        Log.e(TAG, "Successfully deleted");
                                    }

                                    @Override
                                    public void onFailure(String s) {
                                        Log.e(TAG, "Failed to delete: " + s);
                                    }
                                });
                            }
                            // TODO: Remove contact from mContacts
                        }

                        @Override
                        public void onFailure(String s) {
                            Log.e(TAG, "Failed to find headers: " + s);
                        }
                    });
            return true;
        } else {
            return false;
        }
    }

    private void listenForContacts(ContactsListener listener) {
        mContactListeners.add(listener);
        if (mContacts != null) {
            listener.onUpdate(mContacts);
        }
    }

    private void unlistenForContacts(ContactsListener listener) {
        mContactListeners.remove(listener);
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mNotificationHelper = new NotificationHelper(this);
        mNotificationHelper.notifyState(NotificationHelper.State.OFFLINE);
        mTryConnectHandler = new Handler()
        {
            @Override
            public void handleMessage(Message msg)
            {
                tryConnect();
            }
        };
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer();
        mTryConnectHandler.sendEmptyMessage(0);
    }

    private void tryConnect() {
        if (mConnection == null) {
            Preferences preferences = new Preferences(this);
            String userId = preferences.getUserId();
            String userSecret = preferences.getUserSecret();
            // TODO Hash userId to get senderId
            mOurSenderId = userId;
            if ((userId != null) && (userSecret != null))
            {
                mNotificationHelper.notifyState(NotificationHelper.State.CONNECTING);
                String appSecret = getResources().getString(R.string.app_secret);
                mConnection = new QredoConnection() {
                    @Override
                    public void onSuccess(QredoClient client) {
                        Log.i(TAG, "Connected to Qredo: " + client.toString());
                        mClient = client;
                        mNotificationHelper.notifyState(NotificationHelper.State.ONLINE);
                        startContactMonitoring();
                        startRendezvousMonitoring();
                    }

                    @Override
                    public void onFailure(String reason) {
                        Log.e(TAG, "Failure connecting to Qredo: " + reason);
                        stopContactMonitoring();
                        stopRendezvousMonitoring();
                        QredoClient.unbind(this);
                        mClient = null;
                        connectionFailed();
                    }

                    @Override
                    public void onDisconnected() {
                        Log.i(TAG, "Disconnected from Qredo!");
                        stopContactMonitoring();
                        stopRendezvousMonitoring();
                        QredoClient.unbind(this);
                        mClient = null;
                        connectionFailed();
                    }
                };
                QredoClient.bind(appSecret, userId, userSecret, this, mConnection);
            }
            else
            {
                mNotificationHelper.notifyState(NotificationHelper.State.NO_CREDENTIALS);
            }
        }
    }

    private void startRendezvousMonitoring() {
        // TODO: Reinstate previous conversation listeners?
    }

    private void stopRendezvousMonitoring() {
        // TODO: Reinstate previous conversation listeners?
    }

    private void startContactMonitoring() {
        VaultManager vaultManager = mClient.getVaultManager();
        vaultManager.addListener(mVaultItemListener);
        vaultManager.listHeaders(new VaultCallback<Set<VaultItemHeader>>() {
            @Override
            public void onSuccess(final Set<VaultItemHeader> vaultItemHeaders) {
                Log.e(TAG, "vaultManager.listHeaders succeeded");
                mClient.getConversationManager().listAll(new ConversationCallback<Set<Conversation>>() {
                    @Override
                    public void onSuccess(Set<Conversation> conversations) {
                        Log.e(TAG, "ConversationManager.listAll succeeded");
                        Map<String, Conversation> refConversationMap = new HashMap<String, Conversation>();
                        for (Conversation conversation : conversations) {
                            Log.e(TAG, " - Conversation: " + Base64.encodeToString(conversation.getConversationRef().getRef(), Base64.NO_PADDING | Base64.NO_WRAP));
                            refConversationMap.put(Base64.encodeToString(conversation.getConversationRef().getRef(), Base64.NO_PADDING | Base64.NO_WRAP), conversation);
                        }
                        mContacts = new ArrayList<Contact>();
                        for (VaultItemHeader item : vaultItemHeaders) {
                            String conversationRef = item.getItemMetadata().get(KEY_CONVERSATION);
                            Conversation conversation = refConversationMap.get(conversationRef);
                            Log.e(TAG, " - Contact: " + item.getItemMetadata().get(KEY_ID) + ", " + item.getItemMetadata().get(KEY_DISPLAY_NAME) + ", " + conversationRef);
                            if (conversation != null) {
                                Log.e(TAG, "  - Found!");
                                String id = item.getItemMetadata().get(KEY_ID);
                                String displayName = item.getItemMetadata().get(KEY_DISPLAY_NAME);
                                Contact contact = new Contact(id, displayName, conversation);
                                mContacts.add(contact);
                                mClient.getConversationMessageManager().addListener(conversation.getConversationRef(), new LocationMessageListener(contact));
                            } else {
                                // TODO: Tidy up and delete contact
                            }
                        }
                        // TODO: Tidy up and delete uncorrelated conversations
                        notifyContactsListener(false, false);
                    }

                    @Override
                    public void onFailure(String s) {
                        Log.e(TAG, "conversationManager.listAll failed: " + s);
                    }
                });
            }

            @Override
            public void onFailure(String s) {
                Log.e(TAG, "listHeaders failed: " + s);
            }
        });
    }

    private void stopContactMonitoring() {
        mContacts = null;
        notifyContactsListener(true, false);
    }

    private void connectionFailed() {
        mConnection = null;
        mNotificationHelper.notifyState(NotificationHelper.State.COMMUNICATION_FAILURE);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mTryConnectHandler.sendEmptyMessage(0);
            }
        }, CONNECTION_RETRY_INTERVAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        mTryConnectHandler.sendEmptyMessage(0);
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
        mNotificationHelper.notifyState(NotificationHelper.State.OFFLINE);
        if (mTimer != null) {
            mTimer.cancel();
        }
        if (mConnection != null) {
            QredoClient.unbind(mConnection);
        }
        super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind");
        return mBinder;
	}

    private class LocationMessageListener implements ConversationMessageListener {
        Contact mContact;
        public LocationMessageListener(Contact contact) {
            mContact = contact;
        }

        @Override
        public void onReceived(ConversationMessage conversationMessage) {
            Log.e(TAG, "Received message " + conversationMessage.getMetadata(KEY_SENDER) + ", " + conversationMessage.getMetadata(KEY_LOCATION) + ", " + Long.parseLong(conversationMessage.getMetadata(KEY_TIMESTAMP)));
            if (!conversationMessage.getMetadata(KEY_SENDER).equals(mOurSenderId)) {
                mContact.updateLocation(Double.valueOf(conversationMessage.getMetadata(KEY_LATITUDE)),
                        Double.valueOf(conversationMessage.getMetadata(KEY_LONGITUDE)),
                        conversationMessage.getMetadata(KEY_LOCATION),
                        new Date(Long.parseLong(conversationMessage.getMetadata(KEY_TIMESTAMP))));
                notifyContactsListener(false, true);
            }
                mClient.getConversationMessageManager().delete(conversationMessage.getConversationMessageRef(), new ConversationMessageCallback<Boolean>() {
                    @Override
                    public void onFailure(String s) {
                        Log.e(TAG, "ConversationMessageManager(Received).delete failed: " + s);
                    }

                    @Override
                    public void onSuccess(Boolean aBoolean) {
                        Log.e(TAG, "ConversationMessageManager(Received).delete succeeded");
                    }
                });
        }

            @Override
            public void onCounterpartLeft(ConversationMessage conversationMessage) {
                // TODO: Delete conversation and contact?
                mContact.updateLocation(Double.NaN, Double.NaN, "Unknown",
                        new Date(Long.parseLong(conversationMessage.getMetadata(KEY_TIMESTAMP))));
                mClient.getConversationMessageManager().delete(conversationMessage.getConversationMessageRef(), new ConversationMessageCallback<Boolean>() {
                    @Override
                    public void onFailure(String s) {
                        Log.e(TAG, "ConversationMessageManager.delete failed: " + s);
                    }

                    @Override
                    public void onSuccess(Boolean aBoolean) {
                        Log.e(TAG, "ConversationMessageManager.delete succeeded");
                    }
                });
                notifyContactsListener(false, true);
            }

            @Override
            public void onFailure(String s) {
                Log.e(TAG, "ConversationMessageManager.addListener failed: " + s);
            }
    }
}
