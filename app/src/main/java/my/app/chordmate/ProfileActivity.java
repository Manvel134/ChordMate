package my.app.chordmate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.example.chordmate.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private TextView usernameText;
    private TextView chordsAddedText;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private Button saveButton;
    private Button logoutButton;
    private SwitchCompat reminderSwitch;
    private TextView reminderTimeText;
    private View reminderTimeContainer;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userReference;

    // Constants for shared preferences
    private static final String PREFS_NAME = "ChordMatePrefs";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";

    // Default reminder time (8:00 PM)
    private static final int DEFAULT_HOUR = 20;
    private static final int DEFAULT_MINUTE = 0;

    // Shared Preferences
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Check if user is signed in
        if (currentUser == null) {
            // Not signed in, redirect to login activity
            redirectToLogin();
            return;
        }

        // Initialize Firebase Database reference
        userReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("My Profile");

        // Initialize UI components
        usernameText = findViewById(R.id.profile_username);
        chordsAddedText = findViewById(R.id.chords_added_value);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        saveButton = findViewById(R.id.save_button);
        logoutButton = findViewById(R.id.logout_button);

        // Initialize reminder components
        reminderSwitch = findViewById(R.id.remind_switch);
        reminderTimeText = findViewById(R.id.reminder_time_text);
        reminderTimeContainer = findViewById(R.id.reminder_time_container);

        // Set email from FirebaseUser as a fallback
        if (currentUser.getEmail() != null) {
            emailInput.setText(currentUser.getEmail());

            // If we can't access database, at least show the authenticated user's info
            // Email username part (everything before @) as a fallback username
            String email = currentUser.getEmail();
            String fallbackUsername = email.split("@")[0];
            usernameText.setText(fallbackUsername);
        }

        // Load user data from Firebase
        loadUserData();

        // Load and display reminder settings
        loadReminderSettings();

        // Set up save button listener
        saveButton.setOnClickListener(v -> {
            updateUserProfile();
        });

        // Set up logout button listener
        logoutButton.setOnClickListener(v -> {
            logoutUser();
        });

        // Set up reminder switch listener
        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateReminderVisibility(isChecked);
            saveReminderSettings();

            if (isChecked) {
                scheduleReminder();
                Toast.makeText(ProfileActivity.this,
                        "Daily practice reminder enabled",
                        Toast.LENGTH_SHORT).show();
            } else {
                cancelReminder();
                Toast.makeText(ProfileActivity.this,
                        "Daily practice reminder disabled",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Set up reminder time selection
        reminderTimeContainer.setOnClickListener(v -> {
            showTimePickerDialog();
        });
    }

    private void loadReminderSettings() {
        boolean isReminderEnabled = sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, true);
        int hour = sharedPreferences.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR);
        int minute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE);

        // Update UI
        reminderSwitch.setChecked(isReminderEnabled);
        updateReminderVisibility(isReminderEnabled);
        updateReminderTimeText(hour, minute);
    }

    private void updateReminderVisibility(boolean isVisible) {
        reminderTimeContainer.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateReminderTimeText(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String formattedTime = timeFormat.format(calendar.getTime());

        reminderTimeText.setText(formattedTime);
    }

    private void saveReminderSettings() {
        Calendar calendar = Calendar.getInstance();
        String timeString = reminderTimeText.getText().toString();

        try {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            calendar.setTime(timeFormat.parse(timeString));
        } catch (Exception e) {
            calendar.set(Calendar.HOUR_OF_DAY, DEFAULT_HOUR);
            calendar.set(Calendar.MINUTE, DEFAULT_MINUTE);
        }

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_REMINDER_ENABLED, reminderSwitch.isChecked());
        editor.putInt(KEY_REMINDER_HOUR, hour);
        editor.putInt(KEY_REMINDER_MINUTE, minute);
        editor.apply();
    }

    private void showTimePickerDialog() {
        // Get current time from preferences or use default
        int hour = sharedPreferences.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR);
        int minute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, selectedMinute) -> {
                    // Save selected time
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt(KEY_REMINDER_HOUR, hourOfDay);
                    editor.putInt(KEY_REMINDER_MINUTE, selectedMinute);
                    editor.apply();

                    // Update UI
                    updateReminderTimeText(hourOfDay, selectedMinute);

                    // Reschedule reminder with new time
                    if (reminderSwitch.isChecked()) {
                        scheduleReminder();
                        Toast.makeText(ProfileActivity.this,
                                "Reminder time updated",
                                Toast.LENGTH_SHORT).show();
                    }
                },
                hour,
                minute,
                false
        );

        timePickerDialog.show();
    }

    private void scheduleReminder() {
        // Get the alarm time from preferences
        int hour = sharedPreferences.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR);
        int minute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE);

        // Use the ReminderManager utility class to schedule the reminder
        ReminderManager.scheduleReminder(this, hour, minute);
    }

    private void cancelReminder() {
        // Use the ReminderManager utility class to cancel the reminder
        ReminderManager.cancelReminder(this);
    }

    private void loadUserData() {
        // Get additional user data from Realtime Database
        userReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    try {
                        // Try to get username directly first
                        if (dataSnapshot.hasChild("username")) {
                            String username = dataSnapshot.child("username").getValue(String.class);
                            if (username != null && !username.isEmpty()) {
                                usernameText.setText(username);
                            }
                        }
                        // Then try using HelperClass
                        else {
                            HelperClass userData = dataSnapshot.getValue(HelperClass.class);
                            if (userData != null && userData.getUsername() != null) {
                                usernameText.setText(userData.getUsername());
                            }
                        }

                        // You might need to add 'chordsAdded' field to your database
                        chordsAddedText.setText("0");

                    } catch (Exception e) {
                        Toast.makeText(ProfileActivity.this,
                                "Error parsing data: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ProfileActivity.this,
                            "No user data found in database",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ProfileActivity.this,
                        "Permission denied: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();

                // Show a more helpful message about Firebase rules
                Toast.makeText(ProfileActivity.this,
                        "Update Firebase Database Rules in console",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUserProfile() {
        String newEmail = emailInput.getText().toString().trim();
        String newPassword = passwordInput.getText().toString().trim();

        // Update email if changed
        if (!newEmail.equals(currentUser.getEmail()) && !newEmail.isEmpty()) {
            currentUser.updateEmail(newEmail)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Update email in Realtime Database as well
                                userReference.child("email").setValue(newEmail);
                                Toast.makeText(ProfileActivity.this,
                                        "Email updated successfully",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ProfileActivity.this,
                                        "Failed to update email: " + task.getException().getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }

        // Update password if provided
        if (!newPassword.isEmpty()) {
            if (newPassword.length() < 6) {
                passwordInput.setError("Password must be at least 6 characters");
                return;
            }

            currentUser.updatePassword(newPassword)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Toast.makeText(ProfileActivity.this,
                                        "Password updated successfully",
                                        Toast.LENGTH_SHORT).show();
                                passwordInput.setText("");
                            } else {
                                Toast.makeText(ProfileActivity.this,
                                        "Failed to update password: " + task.getException().getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save reminder settings when the activity is paused
        saveReminderSettings();
    }
}