package com.example.roman.testapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.Toast;

import com.example.roman.testapp.jweb.Category;
import com.example.roman.testapp.jweb.Record;

import java.net.InetAddress;
import java.util.Set;


public class MainActivity extends ActionBarActivity 
implements AudioController.AudioPlayerControl,
        MediaController.MediaPlayerControl, MediaPlayer.OnPreparedListener{

    private MediaPlayer mediaPlayer;
    private AudioController audioController;
    private ListView navList;

    private ArrayAdapter navListAdapter;

    /**
     * remain false till media is not completed, inside OnCompletionListener make it true.
     */

    private boolean categoryIsDownloading = false;
    private DrawerLayout mDrawerLayout;
    private ListView recList;
    private RecordsAdapter recAdapter;
    private final String urlE2 = "http://evropa2.cz";
    private final String urlArchiv = "/mp3-archiv/";
    private ActionBar actionBar;
    private String choosenCategory;
    private int lastChoosenCategoryId = -1;
    private View loadingBar;
    private int mAnimationDuration;
    private Category defaultCategory;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
       // setContentView(R.layout.activity_main);
        setContentView(R.layout.main_layout);

        init();
       // Log.d("onCreate", "\n***************\n* VYTVORIL JSEM APPKU\n***************");
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
        //Toast.makeText(this, "Click on " + item.getItemId(), Toast.LENGTH_LONG).show();

        switch(item.getItemId()){
//            case R.id.action_settings :
//                return true;
            case android.R.id.home :
                //Toast.makeText(this, "Home click", Toast.LENGTH_LONG).show();
                //View navigation = findViewById(R.id.left_drawer);
                if(mDrawerLayout.isDrawerOpen(navList)){
                    mDrawerLayout.closeDrawer(navList);
                } else {
                    mDrawerLayout.openDrawer(navList);
                }
                return true;
            case R.id.action_refresh_category :
                downloadCategory();
                return true;
            default : return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
       /* if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }*/
    }

    public void prepareMedia(String url) {
        if (mediaPlayer == null) {
            initMediaPlayer();
        }
        if (!isNetworkConnected()) {
            Toast.makeText(this, "Nejsi připojen k síti", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isInternetAvailable()) {
            //Toast.makeText(this, "Nejsi připojen k internetu", Toast.LENGTH_LONG).show();
            //return;
        }
        PrepareStream ps = new PrepareStream(this, mediaPlayer);
        ps.execute(url);
    }

    public void hideLoading() { loadingBar.setVisibility(View.GONE); }

    public void showLoading(){
        loadingBar.setVisibility(View.VISIBLE);
        loadingBar.setAlpha(1f);
    }

    private void crossfade() {

        // Set the content view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        recList.setAlpha(0f);
        recList.setVisibility(View.VISIBLE);

        // Animate the content view to 100% opacity, and clear any animation
        // listener set on the view.
        recList.animate()
                .alpha(1f)
                .setDuration(mAnimationDuration)
                .setListener(null);

        // Animate the loading view to 0% opacity. After the animation ends,
        // set its visibility to GONE as an optimization step (it won't
        // participate in layout passes, etc.)
        loadingBar.animate()
                .alpha(0f)
                .setDuration(mAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loadingBar.setVisibility(View.GONE);
                    }
                });
    }

    private void init() {
       // Toast.makeText(this, "Initializace...", Toast.LENGTH_SHORT).show();

        downloadCategory();
        loadingBar = findViewById(R.id.loadingPanel);
        loadingBar.setVisibility(View.GONE);
        // Retrieve and cache the system's default "short" animation time.
        mAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        initNavigation();
        initRecList();

        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);


        // Toolbar :it is a generalization of action bars for use within
        // application layouts.
        //Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        // DrawerLayout : it acts as a top-level container for window content
        // that allows for interactive "drawer" views to be
        // pulled out from the edge of the window.
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        //mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        // ActionBarDrawerToggle : This class provides a handy way to tie
        // together the functionality of DrawerLayout and
        // the framework ActionBar to implement the recommended design for
        // navigation drawers.
        final ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this,
                mDrawerLayout, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close){

            @Override
            public void onDrawerOpened(View drawerView) {
                actionBar.setTitle(R.string.category_title);
                super.onDrawerOpened(drawerView);
                //Animation animation = AnimationUtils.makeInAnimation(MainActivity.this, false);

                //invalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                actionBar.setTitle(R.string.app_name);
                actionBar.setSubtitle(choosenCategory);
                super.onDrawerClosed(drawerView);

                //invalidateOptionsMenu();
            }
        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        //Set the ActionBarDrawerToggle in the layout
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        //Hide the default Actionbar
        //getSupportActionBar().hide();
        // Call syncState() from your Activity's onPostCreate to synchronize the
        // indicator
        // with the state of the linked DrawerLayout after
        // onRestoreInstanceState has occurred
        mDrawerToggle.syncState();

        initMediaPlayer();
        initAudioController();

    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(this);
    }

    private void initAudioController(){
        View controllerView = findViewById(R.id.audio_controller);
        audioController = new AudioController(getApplicationContext(), controllerView, this);
    }

    private void initNavigation() {
        //navHeader = (TextView) findViewById(R.id.navHeader);
        // Find the ListView resource.
        navList = (ListView) findViewById(R.id.left_drawer);

//        if (categorySet == null) {
//            Toast.makeText(this, "Chyba při stahování kategorií", Toast.LENGTH_LONG).show();
//            return;
//        }

        Log.d("onCreate", "Kategorie byly stazeny, pridavam do navListu");
        //navListAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, categorySet.toArray());
        navListAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        // Set the ArrayAdapter as the ListView's adapter.
        navList.setAdapter(navListAdapter);
        navList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                //navHeader.setText(categoryItems[position].toString());
                // actionBar.setTitle(categoryItems[position].toString());
                //choosenCategory = categoryItems[position].toString();
                Category item = (Category) parent.getAdapter().getItem(position);
                if (lastChoosenCategoryId == position) {
                    mDrawerLayout.closeDrawer(navList);
                    return;
                }
                lastChoosenCategoryId = position;
                choosenCategory = item.toString();
                mDrawerLayout.closeDrawer(navList);
                //recList.setVisibility(View.GONE);

                //Toast.makeText(MainActivity.this, ""+parent.getAdapter().getItem(position), Toast.LENGTH_LONG).show();
                // Set<Record> recordsSet = item.getRecords();
                if (item.getRecords().isEmpty()) {
                    showLoading();
                    //Toast.makeText(MainActivity.this, "recordsSet is empty", Toast.LENGTH_LONG).show();
                    DownloaderFactory.RecordsDownloader downloader = (DownloaderFactory.RecordsDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Records);
                    downloader.setOnCompleteListener(new ADownloader.OnCompleteListener() {
                        @Override
                        public void onComplete(Object result) {
                            if (result instanceof Category) {
                                fillRecList((Category) result);
                                crossfade();
                            }
                        }
                    });
                    downloader.execute(item);
                    /*try {
                        recordsSet = downloader.get();
                        item.addRecords(recordsSet);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }*/
                    //crossfade();
                } else {
                    fillRecList(item);
                }
                /*hideLoading();
                recList.setVisibility(View.VISIBLE);*/

            }
        });
    }

    private void initRecList() {
        recList = (ListView) findViewById(R.id.listView_records);
        recAdapter = new RecordsAdapter(this);
        recList.setAdapter(recAdapter);
        recList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Record item = (Record) parent.getAdapter().getItem(position);
                Toast.makeText(MainActivity.this, "" + item, Toast.LENGTH_SHORT).show();
                stop();
                prepareMedia(item.getMp3().toString());
            }
        });
        recList.setOnScrollListener(new EndlessScrollListener(new EndlessScrollListener.LoadNextItems() {
            @Override
            public void loadNextItems() {
                recAdapter.downloadNext();
            }
        }));
        recList.setVisibility(View.GONE);
    }

    private void fillNavigation(Set<Category> categorySet) {
        if (categorySet == null) {
            Toast.makeText(this, "Chyba při stahování kategorií", Toast.LENGTH_LONG).show();
            return;
        }
        navListAdapter.addAll(categorySet);
    }

    private void fillRecList(Category category){
        if (category == null) {
            Toast.makeText(this, "Chyba při stahování záznamů", Toast.LENGTH_LONG).show();
            return;
        }
        recAdapter.setSource(category);
    }

    private void downloadCategory(){
        //Set<Category> categorySet = null;
        //Downloader downloader = null;
        DownloaderFactory.CategoriesDownloader downloader = null;
        if (!isNetworkConnected()) {
            Toast.makeText(this, "Nejsi připojen k síti", Toast.LENGTH_LONG).show();
        }
        else if(!categoryIsDownloading){
            Toast.makeText(this, "Stahuji kategorie", Toast.LENGTH_SHORT).show();
//            downloader = new Downloader(this, Downloader.Type.Category, null);
            downloader = (DownloaderFactory.CategoriesDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Categories);
            downloader.setOnCompleteListener(new ADownloader.OnCompleteListener() {
                @Override
                public void onComplete(Object result) {
                    if (result instanceof Set) {
                        fillNavigation((Set<Category>) result);
                        categoryIsDownloading = false;
                    }
                }
            });
            downloader.execute(urlE2 + urlArchiv);
            categoryIsDownloading = true;
        }

    }

    /**
     * Zjistí, jestli je telefon připojen k síti.
     * @return true pokud je připojen
     */
    public boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null;
    }

    /**
     * Zjistí, jestli je telefon připojen k internetu.
     * @return true pokud je připojen
     */
    private boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("google.com"); //You can replace it with your name

            return !ipAddr.equals("");

        } catch (Exception e) {
            return false;
        }

    }

    @Override
    public void next(){
        Toast.makeText(this, "Další", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void previous(){
        Toast.makeText(getApplicationContext(), "Předchozí", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void start() {
        mediaPlayer.start();
    }

    @Override
    public void stop() {
        if(mediaPlayer != null) {
            if (isPlaying()) {
                mediaPlayer.pause();
            }
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
    }

    @Override
    public int getDuration() {
        // if(mediaPlayer != null){
        return mediaPlayer.getDuration() / 1000;
        //  }
        //  return 0;
    }

    @Override
    public int getCurrentPosition() {
        // if(mediaPlayer != null){
        return mediaPlayer.getCurrentPosition() / 1000;
        //}
        //return 0;
    }

    @Override
    public void seekTo(int pos) {
        // if(mediaPlayer != null){
        mediaPlayer.seekTo(pos * 1000);
        //}
    }

    @Override
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return false;
    }

    /**
     * Get the audio session id for the player used by this VideoView. This can be used to
     * apply audio effects to the audio track of a video.
     *
     * @return The audio session, or 0 if there was an error.
     */
    @Override
    public int getAudioSessionId() {
        return 0;
    }

    /**
     * Called when the media file is ready for playback.
     *
     * @param mp the MediaPlayer that is ready for playback
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
//        mediaController.setMediaPlayer(this);
//        mediaController.setAnchorView(findViewById(R.id.audio_controller));
//        //mediaController.setAnchorView(findViewById(R.id.listview_records));
//
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                mediaController.setEnabled(true);
//                mediaController.show(0);
//            }
//        });

        audioController.setEnabled(true);
        audioController.setUpSeekBar();
        /* play mp3 */
        audioController.clickOnPlay();
    }

}
