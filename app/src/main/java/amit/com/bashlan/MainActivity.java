package amit.com.bashlan;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.ClarifaiResponse;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.ConceptModel;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    static final int REQUEST_TAKE_PHOTO = 1;
    private String currentPhotoPath;
    private int picCount = 1;
    private Set<String> photoPaths = new HashSet<>();
    private Map<String, Boolean> ingredients;

    //views
    private LinearLayout mProgressBar;
    private ListView mListview;
    private LinearLayout mListLayout;
    private LinearLayout mLayoutBtnWrapper;
    private LinearLayout mPendingImages;
    private TextView mTxtProgress;
    private ChipGroup mChipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);
        MobileAds.initialize(this, getString(R.string.admob_app_id));
        new ClarifaiBuilder(getString(R.string.clarifai_api_key)).buildSync();
        initView();
    }

    private void initView() {
        mProgressBar = findViewById(R.id.progress_bar);
        mListview = findViewById(R.id.listview);
        mListLayout = findViewById(R.id.list_layout);
        mLayoutBtnWrapper = findViewById(R.id.layout_btn_wrapper);
        mPendingImages = findViewById(R.id.pending_images);
        mTxtProgress = findViewById(R.id.txt_progress);
        mChipGroup = findViewById(R.id.chipGroup);
        findViewById(R.id.txt_update).setOnClickListener(this);
        findViewById(R.id.txt_startover).setOnClickListener(this);
        findViewById(R.id.btn_take_photo).setOnClickListener(this);
        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.txt_update:
                showProgressBar();
                updateProgressText(R.string.text_loading_2);
                getRecipes();
                break;
            case R.id.txt_startover:
                restart();
                break;
            case R.id.btn_take_photo:
                takeNewPhoto();
                break;
        }
    }

    private void takeNewPhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Crashlytics.logException(ex);
                Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
                restart();
                return;
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                        getString(R.string.content_provider_authority),
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            photoPaths.add(currentPhotoPath);
            addCurrentPhotoToStrip();
            dialogShouldTakeAnotherPhoto();
        } else if (requestCode == RESULT_CANCELED) {
            File photo = new File(currentPhotoPath);
            if (photo.exists()) {
                try {
                    boolean delete = photo.delete();
                    Log.v("Photo deleted: ", "" + delete);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            currentPhotoPath = null;
        }
    }

    private void getRecipes(){
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                updateProgressText(R.string.text_loading_3);
                StringBuilder sb = new StringBuilder("http://www.recipepuppy.com/api/?i=");
                for (Map.Entry<String, Boolean> entry : ingredients.entrySet()) {
                    if (entry.getValue()) {
                        sb.append(entry.getKey()).append(",");
                    }
                }
                sb.deleteCharAt(sb.length() - 1);
                URL url;
                try {
                    url = new URL(sb.toString());
                } catch (MalformedURLException e) {
                    Crashlytics.log(e.getMessage());
                    handleError(e.getMessage());
                    return;
                }
                try {
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream inputStream = urlConnection.getInputStream();
                    BufferedReader streamReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    StringBuilder responseStrBuilder = new StringBuilder();
                    String inputStr;
                    while ((inputStr = streamReader.readLine()) != null) {
                        responseStrBuilder.append(inputStr);
                    }
                    JSONObject jsonObject = new JSONObject(responseStrBuilder.toString());
                    final List<Recipe> results = new ArrayList<>();
                    JSONArray jsonArray = jsonObject.getJSONArray("results");
                    // looping through All Contacts
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject c = jsonArray.getJSONObject(i);
                        String title = c.getString("title");
                        String href = c.getString("href");
                        String ingredients = c.getString("ingredients");
                        String thumbnail = c.getString("thumbnail");
                        results.add(new Recipe(title, href, ingredients, thumbnail));
                    }
                    populateList(results);
                } catch (IOException | JSONException e) {
                    Crashlytics.log(e.getMessage());
                    handleError(e.getMessage());
                }
            }});
    }

    private void dialogShouldTakeAnotherPhoto() {
        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle(String.format(getString(R.string.photo_taken_dialog_title),picCount))
                .setMessage(getString(R.string.photo_taken_dialog_body))
                .setPositiveButton(getString(R.string.photo_taken_dialog_positive), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        picCount++;
                        takeNewPhoto();
                    }
                })
                .setNeutralButton(getString(R.string.photo_taken_dialog_neutral), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        showProgressBar();
                        updateProgressText(R.string.text_loading_1);
                        sendRequest();
                    }
                }).create().show();
    }

    private void showProgressBar() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setVisibility(View.VISIBLE);
                mLayoutBtnWrapper.setVisibility(View.GONE);
                mListLayout.setVisibility(View.GONE);
            }
        });
    }

    private void sendRequest() {
        final Set<ClarifaiInput> inputs = new HashSet<>();
        for (String path : photoPaths) {
            inputs.add(ClarifaiInput.forImage(new File(path)));
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ClarifaiClient client = new ClarifaiBuilder(getString(R.string.clarifai_api_key))
                        .buildSync();
                ConceptModel model = client.getDefaultModels().foodModel();
                ClarifaiResponse<List<ClarifaiOutput<Concept>>> response = model.predict()
                        .withInputs(inputs)
                        .executeSync();
                if (response.isSuccessful()) {
                    updateProgressText(R.string.text_loading_2);
                    List<ClarifaiOutput<Concept>> clarifaiOutputs = response.get();
                    ingredients = new HashMap<>();
                    String name;
                    for (int i = 0; i < clarifaiOutputs.size(); i++) {
                        for (Concept concept : clarifaiOutputs.get(i).data()) {
                            name = concept.name();
                            if (concept.value() > 0.75 && name != null) {
                                ingredients.put(name, true);
                            }
                        }
                    }
                    setChips();
                    getRecipes();
                } else {
                    Crashlytics.log(response.getStatus().statusCode(), "RESPONSE UNSUCCESSFUL", response.getStatus().description());
                    if (response.getStatus().errorDetails() != null) {
                        Crashlytics.log(response.getStatus().statusCode(), "RESPONSE UNSUCCESSFUL", "Error details: " + response.getStatus().errorDetails());
                    }
                    handleError(response.getStatus().description());
                }
            }
        });
    }

    private void addCurrentPhotoToStrip() {
        ImageView imgView = new ImageView(MainActivity.this);
        imgView.setLayoutParams(new LinearLayout.LayoutParams(250, 250));
        imgView.setPadding(10, 0, 10, 0);
        Glide.with(MainActivity.this)
                .load(currentPhotoPath)
                .override(250, 250)
                .into(imgView);
        mPendingImages.addView(imgView);
    }


    private void setChips() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (final Map.Entry<String, Boolean> entry : ingredients.entrySet()) {
                    final Chip chip = new Chip(MainActivity.this, null, R.style.Widget_MaterialComponents_Chip_Filter);
                    chip.setText(entry.getKey());
                    chip.setClickable(true);
                    chip.setCheckable(true);
                    chip.setChecked(true);
                    mChipGroup.addView(chip);
                    chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            ingredients.put(entry.getKey(), isChecked);
                        }
                    });
                }
            }
        });
    }


    private void populateList(final List<Recipe> recipes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RecipesAdapter adapter = new RecipesAdapter(MainActivity.this, recipes);
                mListview.setAdapter(adapter);
                mProgressBar.setVisibility(View.GONE);
                mPendingImages.removeAllViewsInLayout();
                mPendingImages.setVisibility(View.GONE);
                mListLayout.setVisibility(View.VISIBLE);
                mListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(recipes.get(position).getHref()));
                        startActivity(intent);
                    }
                });
            }
        });
        cleanUp();
    }

    private void cleanUp() {
        String parentDir = null;
        for (String path : photoPaths) {
            if (new File(path).exists()) {
                parentDir = new File(path).getParent();
                boolean delete = new File(path).delete();
                Log.v("Photo deleted: ", "" + delete);
            }
        }
        if(parentDir != null && new File(parentDir).listFiles().length > 0){
            File[] files = new File(parentDir).listFiles();
            for(File photo : files){
                boolean delete = photo.delete();
                Log.v("Photo deleted: ", "" + delete);
            }
        }
    }

    private void updateProgressText(int resource) {
        mTxtProgress.setText(resource);
    }

    private void handleError(String message) {
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        restart();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                "JPEG_" + timeStamp + "_",
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void restart() {
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

}
