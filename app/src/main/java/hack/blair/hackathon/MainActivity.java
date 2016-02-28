package hack.blair.hackathon;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int UPDATE = 0;
    private static final int FAILURE = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;
    private ServiceConnection mServiceConnection;
    private QredoService.LocalBinder mService;
    private List<Contact> mContacts;
    private QredoService.ContactsListener mContactsListener = new QredoService.ContactsListener() {
        @Override
        public void onUpdate(List<Contact> contacts) {
            Log.e(TAG, "ContactsListener.onUpdate - " + contacts.size());
            mContacts = contacts;
            mContactsUpdateHandler.sendEmptyMessage(UPDATE);
        }

        @Override
        public void onFailure() {
            mContacts = null;
            mContactsUpdateHandler.sendEmptyMessage(FAILURE);
        }
    };
    private Handler mContactsUpdateHandler;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");
		setContentView(R.layout.activity_main);
        Preferences preferences = new Preferences(this);
        if ((preferences.getUserId() == null) || (preferences.getUserId() == null)) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        } else {
            // TODO Request location if not already granted
            //if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
            //            REQUEST_ACCESS_FINE_LOCATION);
            //}

            final ListView contactsList = (ListView)findViewById(R.id.list_contact);
            final ArrayAdapter<Contact> adapter = new ArrayAdapter<Contact>(this, R.layout.list_entry_contact) {
                DateFormat mFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.LONG);

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    Log.e(TAG, "ArrayAdapter.getView being invoked at position " + position);
                    View v = convertView;

                    if (v == null) {
                        LayoutInflater vi;
                        vi = LayoutInflater.from(getContext());
                        v = vi.inflate(R.layout.list_entry_contact, null);
                    }

                    Contact item = getItem(position);
                    if (item != null) {
                        Log.e(TAG, "Got contact " + item.getDisplayName());
                        ((TextView)v.findViewById(R.id.text_contact)).setText(item.getDisplayName());
                        ((TextView)v.findViewById(R.id.text_location)).setText(item.getLocation());
                        ((TextView)v.findViewById(R.id.text_time)).setText(mFormat.format(item.getTimestamp()));
                    }
                    return v;
                }
            };
            contactsList.setAdapter(adapter);
            mContactsUpdateHandler = new Handler() {
                @Override
                public void handleMessage(Message msg)
                {
                    switch (msg.what) {
                        case UPDATE:
                            Log.e(TAG, "ContactsUpdateHandler handling UPDATE");
                            List<Contact> contacts = mContacts;
                            adapter.clear();
                            if (mContacts != null) {
                                Log.e(TAG, "Adding " + mContacts.size() + " contacts");
                                adapter.addAll(mContacts);
                            }
                            adapter.notifyDataSetChanged();
                            break;

                        case FAILURE:
                            Log.e(TAG, "ContactsUpdateHandler handling FAILURE");
                            Toast.makeText(MainActivity.this, "Contacts synchronization failed", Toast.LENGTH_SHORT).show();
                            adapter.clear();
                            adapter.notifyDataSetChanged();
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
                    mService.listenForContacts(mContactsListener);
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
            }

            Button buttonAddContact = (Button)findViewById(R.id.button_add_contact);
            buttonAddContact.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, AddContactActivity.class);
                    startActivity(intent);
                }
            });
        }
	}

    @Override
    protected void onDestroy() {
        if (mServiceConnection != null) {
            if (mService != null) {
                mService.unlistenForContacts(mContactsListener);
            }
            unbindService(mServiceConnection);
            mServiceConnection = null;
            mService = null;
        }
        super.onDestroy();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
        if (id == R.id.action_logout) {
            Preferences preferences = new Preferences(this);
            preferences.setUserIdPassword(null, null);
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return true;
        }
		return super.onOptionsItemSelected(item);
	}
}
