package com.example.myapplication;

import me.vidv.vidvlivenesssdk.sdk.CapturedActions;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessConfig;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessListener;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessResponse;
import me.vidv.vidvocrsdk.sdk.BuilderError;
import me.vidv.vidvocrsdk.sdk.CapturedImages;
import me.vidv.vidvocrsdk.sdk.ServiceFailure;
import me.vidv.vidvocrsdk.sdk.Success;
import me.vidv.vidvocrsdk.sdk.UserExit;
import me.vidv.vidvocrsdk.sdk.VIDVOCRConfig;
import me.vidv.vidvocrsdk.sdk.VIDVOCRListener;
import me.vidv.vidvocrsdk.sdk.VIDVOCRResponse;
import me.vidv.vidvocrsdk.viewmodel.VIDVError;
import me.vidv.vidvocrsdk.viewmodel.VIDVEvent;
import me.vidv.vidvocrsdk.viewmodel.VIDVLogListener;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private VIDVOCRConfig.Builder vidvOcrBuilder;
    private VIDVOCRListener vidvOcrListener;
    private VIDVLivenessListener vidvLivenessListener;
    private VIDVLivenessConfig.Builder livenessBuilder;
    private com.example.myapplication.AccessTokenGenerator accessTokenGenerator;
    private com.example.myapplication.AccessTokenResponse accessTokenResponse;

    // Credintials are put in App interface for testing purpose
    private String username = "Put your actual username";
    private String password = "Put your actual Password";
    private String clientId = "Put your Client ID";
    private String clientSecret = "Put your Client Secret";
    private String bundleKey = "Put your bundlekey";
    private String baseUrl = "Put your environment base URL";
    private String accessToken = "";
    private String TransactionIdFront;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanButton = findViewById(R.id.scanButton);
        //Intialize builder for OCR & Liveness
        vidvOcrBuilder = new VIDVOCRConfig.Builder();
        livenessBuilder= new VIDVLivenessConfig.Builder();

        //Create an instance of AccessTokenGenerator to generate access tokens for authentication
        accessTokenGenerator = new AccessTokenGenerator();
        accessTokenResponse = new AccessTokenResponse();

        // Button click to start the OCR SDK
        scanButton.setOnClickListener(view -> generateToken());
    }

    private void generateToken() {
        accessTokenGenerator.generateAccessToken(username, password, clientId, clientSecret,baseUrl, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        Toast.makeText(MainActivity.this, "Access token generated", Toast.LENGTH_SHORT).show();
                        accessToken = tokenResponse.getAccessToken();
                        Log.d("Access Token", accessToken);

                        //Start OCR SDK
                        configureAndStartOCR(accessToken);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        // Handle the failure
                        Toast.makeText(MainActivity.this, "Failed to get access token: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void configureAndStartOCR(String accessToken){

            // Configure the SDK with the mandatory configurations
            vidvOcrBuilder.setBaseUrl(baseUrl)
                    .setAccessToken(accessToken)
                    .setBundleKey(bundleKey)

                    // This is an example of optional configurations
                    .setLanguage("en")
                    .setReviewData(true);

        // Set a logs listener for the VIDVOCR builder to handle logs and errors during the OCR process
        vidvOcrBuilder.setLogsListener(new VIDVLogListener() {
                @Override
                public void onLog(VIDVEvent log) {
                    Log.d("VIDV-Logs", "Key: " + log.getKey() + ", " + "Type: " + log.getType() + ", " + "Date: " + log.getDate() + ", " + "Screen: " + log.getScreen());

                }

                @Override
                public void onLog(VIDVError log) {
                    Log.d("VIDV-Error", "Code: " + log.getCode() + ", " + "Message: " + log.getMessage() + ", " + "Date: " + log.getDate() + ", " + "Screen: " + log.getScreen() + ", " + "Type: " + log.getType());
                }
            });
        // Create  a VIDVOCRListener to handle  OCR result
            vidvOcrListener= new VIDVOCRListener() {
                @Override
                public void onOCRResult(VIDVOCRResponse response) {
                    runOnUiThread(() -> {
                        if (response instanceof Success) {
                            //This is excueted when the OCR transaction is completed successfully.
                            Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_SHORT).show();

                            //Get transaction ID to be able to apply facematch on liveness SDK
                            TransactionIdFront = ((Success) response).getData().getOcrResult().getTransactionIdFront();

                            //an example code to use the response in your app :
                            String firstName = ((Success) response).getData().getOcrResult().getFirstName();

                            //Call liveness function
                            configureAndStartLiveness(TransactionIdFront);

                        } else if (response instanceof CapturedImages) {
                            //This returns captured images in real time.
                            Toast.makeText(MainActivity.this, "Captured Images"  , Toast.LENGTH_SHORT).show();
                        } else if (response instanceof UserExit) {
                            //This is excuted when the user exists the SDK
                            Toast.makeText(MainActivity.this, "User Exit" + ((UserExit)response).getStep(), Toast.LENGTH_SHORT).show();
                        } else if (response instanceof ServiceFailure) {
                            //This happens when a process is finished with the user's failure to pass the service requirements
                            Toast.makeText(MainActivity.this, "Service Failure: " + ((ServiceFailure) response).getMessage(), Toast.LENGTH_SHORT).show();
                        } else if (response instanceof BuilderError) {
                            //This happens when a process is terminated due to an error in the builder
                            Toast.makeText(MainActivity.this, "Builder Error: " + ((BuilderError) response).getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

                }


            };
            // start SDK
            vidvOcrBuilder.start(MainActivity.this, vidvOcrListener);
    }

    private void configureAndStartLiveness(String TransactionIdFront) {
        // Configure the SDK with the mandatory configurations
        livenessBuilder.setBaseUrl(baseUrl)
                .setAccessToken(accessToken).setBundleKey(bundleKey).setFrontTransactionId(TransactionIdFront);

        //Create  a VIDVOCRListener to handle  Liveness result
        vidvLivenessListener = new VIDVLivenessListener() {
            @Override
            public void onLivenessResult(VIDVLivenessResponse livenessResponse) {
                runOnUiThread(() -> {
                    if (livenessResponse instanceof me.vidv.vidvlivenesssdk.sdk.Success) {
                        //This is excueted when the OCR transaction is completed successfully.
                        Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_SHORT).show();

                    } else if (livenessResponse instanceof me.vidv.vidvlivenesssdk.sdk.BuilderError) {
                        //This happens when a process is terminated due to an error in the builder
                        Toast.makeText(MainActivity.this, "Builder Error: " + ((me.vidv.vidvlivenesssdk.sdk.BuilderError) livenessResponse ).errorMessage, Toast.LENGTH_SHORT).show();
                    } else if (livenessResponse instanceof me.vidv.vidvlivenesssdk.sdk.ServiceFailure) {
                        //This happens when a process is finished with the user's failure to pass the service requirements
                        Toast.makeText(MainActivity.this, "Service Error: " + ((me.vidv.vidvlivenesssdk.sdk.ServiceFailure) livenessResponse ).errorMessage, Toast.LENGTH_SHORT).show();
                        if (Objects.equals(((me.vidv.vidvlivenesssdk.sdk.ServiceFailure) livenessResponse).errorMessage, "You did not match the face in the ID!")){

                            onFaceMatchFail();
                        }
                    } else if (livenessResponse instanceof me.vidv.vidvlivenesssdk.sdk.UserExit) {
                        //This is executed when the user exists the SDK
                        Toast.makeText(MainActivity.this, "User Exit", Toast.LENGTH_SHORT).show();
                    }else if (livenessResponse instanceof CapturedActions){
                        //This returns captured images in real time.
                        Toast.makeText(MainActivity.this, "Captured Images"  , Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };
        //Start liveness
        livenessBuilder.start(MainActivity.this,vidvLivenessListener);
    }

    // When face match fails
    private void onFaceMatchFail() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Match Failed");
        builder.setMessage("The face does not match with the ID.");

        // First button to restart the entire process (OCR + Liveness)
        builder.setPositiveButton("Repeat OCR & Liveness", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Call the method to configure and start OCR and Liveness again
                configureAndStartOCR(accessToken);  // Assuming this method restarts the whole process
            }
        });

        // Second button to only repeat liveness check
        builder.setNegativeButton("Repeat Liveness", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Call the method to configure and start Liveness only
                configureAndStartLiveness(TransactionIdFront);  // Assuming this method repeats liveness check
            }
        });

        // Show the dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}

