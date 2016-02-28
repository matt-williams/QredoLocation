package hack.blair.hackathon;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.qredo.device.android.conversation.Conversation;
import com.qredo.device.android.rendezvous.Rendezvous;

import org.spongycastle.crypto.tls.TlsAgreementCredentials;

public class AddContactActivity extends Activity {
    private static final String TAG = "AddContactActivity";
    private static final int REQUEST_PICK_CONTACT = 1;
    private static final int ON_GOT_RENDEZVOUS = 1;
    private static final int ON_SENT_RENDEZVOUS = 2;
    private static final int ON_GOT_CONVERSATION = 3;
    private static final int ON_FAILURE = 4;
    private NfcAdapter mNfcAdapter;
    private boolean mPaused = true;
    private String mOurRendezvous;
    private String mTheirRendezvous;
    private ServiceConnection mServiceConnection;
    private QredoService.LocalBinder mService;
    private TextView mTextPrompt;
    private ProgressBar mSpinner;
    private Conversation mConversation;
    private Handler mCallbackHandler;
    QredoService.RendezvousConversationCallback mCallback = new QredoService.RendezvousConversationCallback() {
        @Override
        public void onGotRendezvous(Rendezvous rendezvous) {
            mOurRendezvous = rendezvous.getTag();
            if (mCallbackHandler != null) {
                mCallbackHandler.sendEmptyMessage(ON_GOT_RENDEZVOUS);
            }
        }

        @Override
        public void onGotConversation(Conversation conversation) {
            mConversation = conversation;
            if (mCallbackHandler != null) {
                mCallbackHandler.sendEmptyMessage(ON_GOT_CONVERSATION);
            }
        }

        @Override
        public void onFailure() {
            if (mCallbackHandler != null) {
                mCallbackHandler.sendEmptyMessage(ON_FAILURE);
            }
        }
    };

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_contact);
        mTextPrompt = (TextView)findViewById(R.id.textPrompt);
        mSpinner = (ProgressBar)findViewById(R.id.progressBar);
        mTextPrompt.setText("Waiting for Qredo service...");
        mSpinner.setVisibility(View.VISIBLE);

        mOurRendezvous = null;
        mTheirRendezvous = null;

        mCallbackHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ON_GOT_RENDEZVOUS:
                        Log.e(TAG, "Handling ON_GOT_RENDEZVOUS...");
                        if (mTheirRendezvous == null) {
                            if (!mPaused) {
                                startDispatch(mOurRendezvous);
                            }
                            mTextPrompt.setText("Touch your contact's phone");
                            mSpinner.setVisibility(View.GONE);
                        }
                        break;

                    case ON_GOT_CONVERSATION:
                        Log.e(TAG, "Handling ON_GOT_CONVERSATION...");
                        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                        startActivityForResult(intent, REQUEST_PICK_CONTACT);
                        break;

                    case ON_SENT_RENDEZVOUS:
                        Log.e(TAG, "Handling ON_SENT_RENDEZVOUS...");
                        mTextPrompt.setText("Waiting for conversation from contact...");
                        mSpinner.setVisibility(View.VISIBLE);
                        break;

                    case ON_FAILURE:
                        Log.e(TAG, "Handling ON_FAILURE...");
                        Toast.makeText(AddContactActivity.this, "Failed to get rendezvous from Qredo", Toast.LENGTH_SHORT).show();
                        finish();
                        break;
                }

            }
        };

        Intent intent = new Intent(this, QredoService.class);
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.e(TAG, "onServiceConnected");
                mService = (QredoService.LocalBinder)service;
                if (mTheirRendezvous == null) {
                    mTextPrompt.setText("Requesting rendezvous with contact...");
                    mSpinner.setVisibility(View.VISIBLE);
                    mService.rendezvousConversation(mCallback);
                } else {
                    mTextPrompt.setText("Establishing conversation with contact...");
                    mSpinner.setVisibility(View.VISIBLE);
                    mService.acceptRendezvous(mTheirRendezvous, mCallback);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.e(TAG, "onServiceDisconnected");
                mService = null;
            }
        };
        Log.e(TAG, "issuing bindService");
        startService(intent);
        if (!bindService(intent, mServiceConnection, 0)) {
            Log.e(TAG, "bindService failed");
            Toast.makeText(this, "Failed to bind to Qredo service", Toast.LENGTH_SHORT).show();
        }

        intent = getIntent();
        if (intent != null) {
            handleIntent(intent);
        }

        if (mTheirRendezvous == null) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                Toast.makeText(this, "Failed to access NFC", Toast.LENGTH_SHORT).show();
                finish();
            } else if (!mNfcAdapter.isEnabled()) {
                Toast.makeText(this, "NFC is disabled - please enable it", Toast.LENGTH_SHORT).show();
                finish();
            } else if (mService != null) {
                mTextPrompt.setText("Requesting rendezvous with contact...");
                mSpinner.setVisibility(View.VISIBLE);
                mService.rendezvousConversation(mCallback);
            }
        }
	}

    private void startDispatch(String rendezvous) {
        mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[]{NdefRecord.createUri("http://github.com/matt-williams/QredoLocation/?rendezvous=" + rendezvous)}), this);
        mNfcAdapter.setOnNdefPushCompleteCallback(new NfcAdapter.OnNdefPushCompleteCallback() {
            @Override
            public void onNdefPushComplete(NfcEvent event) {
                mCallbackHandler.sendEmptyMessage(ON_SENT_RENDEZVOUS);
            }
        }, this, this);
        // mNfcAdapter.enableForegroundDispatch(this, ???, null, null);
    }

    private void handleIntent(Intent intent) {
        if ((intent.getAction() != null) &&
            (intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED))) {
            Tag intentTag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndefTag = Ndef.get(intentTag);
            mTheirRendezvous = ndefTag.getCachedNdefMessage().getRecords()[0].toUri().getQueryParameter("rendezvous");
            android.util.Log.e(TAG, "Got their rendezvous: " + mTheirRendezvous);
            if (mService != null) {
                mTextPrompt.setText("Establishing conversation with contact...");
                mSpinner.setVisibility(View.VISIBLE);
                mService.acceptRendezvous(mTheirRendezvous, mCallback);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
        if ((mOurRendezvous != null) && (mTheirRendezvous == null) && (mNfcAdapter != null)) {
            startDispatch(mOurRendezvous);
        }
    }

    @Override
    public void onPause() {
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
        mPaused = true;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mServiceConnection = null;
            mService = null;
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        switch (reqCode) {
            case (REQUEST_PICK_CONTACT):
                if (resultCode == Activity.RESULT_OK) {
                    Uri contactUri = data.getData();
                    Log.e(TAG, "Got contact URI: " + contactUri);

                    ContentResolver resolver = getContentResolver();
                    Cursor cursor = resolver.query(contactUri,
                            new String[] {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                            null,
                            null,
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");
                    cursor.moveToFirst();
                    String id = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    if (mService != null) {
                        Log.e(TAG, "Got service - adding contact");
                        mService.addContact(new Contact(id, displayName, mConversation));
                        Toast.makeText(this, "Added contact " + displayName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Qredo service unavailable!", Toast.LENGTH_SHORT).show();
                    }
                }
                finish();
                break;
        }
    }
}
