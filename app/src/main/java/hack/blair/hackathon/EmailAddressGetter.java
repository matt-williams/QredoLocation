package hack.blair.hackathon;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

public class EmailAddressGetter implements LoaderManager.LoaderCallbacks<Cursor> {
	public interface Callback {
		public void onSuccess(String emailAddress);
		public void onFailure();
	}
	
	private Context mContext;
	private Callback mCallback;

	public EmailAddressGetter(Activity activity, Callback callback) {
		mContext = activity;
		mCallback = callback;
		activity.getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(mContext,
            Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
            new String[] {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
            },
            ContactsContract.Contacts.Data.MIMETYPE + " = ?",
			new String[] {ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE},
            ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        data.moveToFirst();
        if (!data.isAfterLast()) {
            mCallback.onSuccess(data.getString(0));
        } else {
            mCallback.onFailure();
        }
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}
}
