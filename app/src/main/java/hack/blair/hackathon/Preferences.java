package hack.blair.hackathon;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;

public class Preferences {
    private static final String PREFERENCES = "QredoService.Preferences";
    private static final String PREFERENCE_USER_ID = "userId";
    private static final String PREFERENCE_USER_SECRET = "userSecret";

    SharedPreferences mPreferences;

    public Preferences(Context context) {
        mPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    String getUserId() {
        return mPreferences.getString(PREFERENCE_USER_ID, null);
    }

    String getUserSecret() {
        return mPreferences.getString(PREFERENCE_USER_SECRET, null);
    }

    public void setUserIdPassword(String userId, String userSecret) {
        SharedPreferences.Editor editor = mPreferences.edit();
        if (!userId.isEmpty()) {
            editor.putString(PREFERENCE_USER_ID, userId);
        } else {
            editor.remove(PREFERENCE_USER_ID);
        }
        if (!userSecret.isEmpty()) {
            editor.putString(PREFERENCE_USER_SECRET, userSecret);
        } else {
            editor.remove(PREFERENCE_USER_SECRET);
        }
        editor.commit();
    }
}
