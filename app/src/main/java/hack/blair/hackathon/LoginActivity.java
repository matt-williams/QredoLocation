package hack.blair.hackathon;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends Activity {
	private static final String EXTRA_USERNAME = "Username";
	private static final String EXTRA_PASSWORD = "Password";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		Preferences preferences = new Preferences(this);
		String username = preferences.getUserId();
		String password = preferences.getUserSecret();
		final EditText inputUsername = (EditText)findViewById(R.id.inputUsername);
		final EditText inputPassword = (EditText)findViewById(R.id.inputPassword);
		if (username != null) {
			inputUsername.setText(username);
		} else {
			new EmailAddressGetter(this, new EmailAddressGetter.Callback() {
				@Override
				public void onSuccess(String emailAddress) {
					if (inputUsername.getText().length() == 0) {
						inputUsername.setText(emailAddress);
					}
				}
				@Override
				public void onFailure() {}
			});
		}
	    inputPassword.setText(password);

		Button buttonLogin = (Button)findViewById(R.id.buttonLogin);
		buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Preferences preferences = new Preferences(LoginActivity.this);
                preferences.setUserIdPassword(inputUsername.getText().toString(), inputPassword.getText().toString());
                Intent intent = new Intent(LoginActivity.this, QredoService.class);
                startService(intent);
                intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
	}
}
