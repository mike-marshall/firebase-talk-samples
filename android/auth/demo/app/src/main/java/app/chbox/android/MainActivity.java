package app.chbox.android;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.UserInfo;

import static java.sql.Types.TIMESTAMP;

/**
 *
 */
public class MainActivity extends AppCompatActivity {

    private int RC_SIGN_IN = 9001;

    public ImageView profileView;
    private TextView nameHeader;
    private TextView lastLogin;
    private boolean loggedIn = false;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        profileView = (ImageView) this.findViewById(R.id.profile_img);
        nameHeader = (TextView) this.findViewById(R.id.name_header);
        lastLogin = (TextView) this.findViewById(R.id.last_login);

        FirebaseApp.initializeApp(this);
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.GoogleBuilder().build(),
                new AuthUI.IdpConfig.FacebookBuilder().build(),
                new AuthUI.IdpConfig.TwitterBuilder().build(),
                new AuthUI.IdpConfig.EmailBuilder().build());

        FirebaseAuth authInst = FirebaseAuth.getInstance();

        fab = findViewById(R.id.fab);

        // check if we're already logged in
        // if so, retrieve user details
        if (authInst != null) {
            FirebaseUser user = authInst.getCurrentUser();

            if (user != null) {
                Log.i("auth", "provider: " + user.getProviderId());
                loadUser(user);
            }

        }


        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Button function changes depending on if we
                // are logged in or not
                if (loggedIn) {
                    logOut();
                } else {
                    // Launch Firebase AuthUI logon activity with the providers
                    // we registered earlier
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(providers)
                                    .setAlwaysShowSignInMethodScreen(true)
                                    .build(),
                            RC_SIGN_IN);
                }
            }

        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            Log.i("auth", response.getProviderType());

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                loadUser(user);

                // ...
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }

    /**
     * @param user
     */
    private void loadUser(FirebaseUser user) {
        try {
            loggedIn = true;

            fab.setImageResource(R.drawable.ic_log_out);

            String urlText = getLargeProfileImageURL(user);

            // get profile image asynchronously
            new DownloadImageTask().execute(urlText);

            // set name
            nameHeader.setText(user.getDisplayName());

            // set last login text
            long timeStamp = user.getMetadata().getLastSignInTimestamp();
            SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            String dateString = formatter.format(new Date(timeStamp));
            lastLogin.setText(dateString);
        }
        catch (Exception other) {
            Log.e("other Ex", "Error loading profile image", other);
        }
    }

    private void logOut() {
        // log out
        AuthUI.getInstance().signOut(this);

        // Set button back to login icon
        fab.setImageResource(android.R.drawable.ic_lock_idle_lock);

        // set profile image back to generic placeholder and clear text views
        loggedIn = false;
        nameHeader.setText("--");
        lastLogin.setText(R.string.last_login_label);

        profileView.setImageResource(R.drawable.no_profile);
    }

    private String getLargeProfileImageURL(FirebaseUser user) {
        String providerType = "";
        List<? extends UserInfo> info = user.getProviderData();

        // Get first provider that is not "firebase", this will be the
        // provider associated with the current login
        //
        // HINT: FirebaseUser.getProviderId() will always return "firebase", so we
        // need a different way to determine the current provider
        for (Integer i = 0; i < info.size(); i++) {
            UserInfo ui = info.get(i);
            Log.i("auth", i.toString() + ": " + ui.getEmail() + ", " + ui.getProviderId());

            // order of the provider list seems random, need to exclude
            // firebase and take the next viable provider as the selected one
            if (!ui.getProviderId().contains("firebase")) {
                providerType = ui.getProviderId();
                break;
            }
        }

        String urlText = user.getPhotoUrl().toString();

        // each provider can have a different image URL layout
        // and specifies retrieval of "large" images in different ways
        if (providerType.contains("twitter")) {
            urlText = urlText.replace("_normal", "");
        } else if (providerType.contains("facebook")) {
            urlText = urlText + "?type=large";
        }

        return urlText;
    }

    /** Class used for asynchronous image retrieval from a URL
     *
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {

        protected void onPreExecute() {

        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap profilePic = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                profilePic = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", "image download error");
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return profilePic;
        }

        protected void onPostExecute(Bitmap result) {
            // Change profile image
            MainActivity.this.profileView.setImageBitmap(result);
        }
    }
}
