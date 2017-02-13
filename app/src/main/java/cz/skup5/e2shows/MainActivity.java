package cz.skup5.e2shows;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import cz.skup5.e2shows.DownloaderFactory.CoverImageDownloader;
import cz.skup5.e2shows.DownloaderFactory.MediaUrlDownloader;
import cz.skup5.e2shows.DownloaderFactory.RecordsDownloader;
import cz.skup5.e2shows.DownloaderFactory.ShowsDownloader;
import cz.skup5.e2shows.manager.BasicPlaylistManager;
import cz.skup5.e2shows.playlist.PlaylistManager;
import cz.skup5.e2shows.record.RecordItem;
import cz.skup5.e2shows.record.RecordItemViewHolder;
import cz.skup5.e2shows.record.RecordType;
import cz.skup5.e2shows.record.RecordsAdapter;
import cz.skup5.e2shows.show.ShowItem;
import cz.skup5.e2shows.show.ShowsAdapter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.skup5.jEvropa2.data.Show;

/**
 * Main class of application and the only activity.
 *
 * @author Roman Zelenik
 */
public class MainActivity extends AppCompatActivity {

  public static final String
          DOWNLOADING_SHOWS = "Stahuji seznam Show...",
          ERROR_ON_LOADING = "Při načítání došlo k chybě :-(",
          ERROR_NO_CONNECTION = "Nejsi připojen k síti",
          LOADING = "Načítání",
          SHOWS_ARE_READY = "Show jsou připraveny",
          STILL_DOWNLOADING = "Stahování probíhá...",
          STORNO = "Storno",
          SUB_URL_ARCHIV = "/mp3-archiv/",
          SUB_URL_SHOWS = "/shows/",
          TRY_NEXT_RECORD = "Zkus další",
          URL_E2 = "https://evropa2.cz";

  private static final int
          ITEM_OFFSET = 6,
          VISIBLE_TRESHOLD = 5;

  private static final PlaylistManager playlistManager = BasicPlaylistManager.getInstance();

  private MediaPlayer mediaPlayer;
  private AudioController<RecordItem> audioController;
  private AudioController.AudioPlayerControl audioPlayerControl;

  private SwipeRefreshLayout swipeRefreshLayout;
  private RecyclerView recordsList;
//  private RecordsAdapter recordsAdapter;

  private ListView showsList;
  private ShowsAdapter showsAdapter;

  private boolean
          recordsAreDownloading = false,
          showsAreDownloading = false;

  private Menu menu;
  private DrawerLayout mDrawerLayout;
  private ActionBar actionBar;
  private RecordItem chosenRecord;
  private ShowItem playShow;
  private int chosenShowPosition = -1;
  private View loadingBar;
  private int crossfadeAnimDuration;
  private View refreshShowButton;
  private Animation refreshShowAnim;
  private Map<String, Integer> selectedRecords;

    /*#######################################################
      ###               STATIC METHODS                    ###
      #######################################################*/

  public static Animation createRotateAnim(View animatedView, int toDegrees, int duration, boolean infinite) {
    Animation anim = new RotateAnimation(0, toDegrees,
            animatedView.getWidth() / 2, animatedView.getHeight() / 2);
    anim.setDuration(duration);
    if (infinite) {
      anim.setRepeatMode(Animation.INFINITE);
    }
    anim.setInterpolator(new LinearInterpolator());
    return anim;
  }

  public static void errorReportsDialog(Context context, List<String> reports) {
    String msg = "Došlo k ";
    msg += reports.size() > 1 ? "několika chybám." : "chybě.";
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1) {
      @Override
      public boolean isEnabled(int position) {
        return false;
      }
    };
    adapter.addAll(reports);
    new AlertDialog.Builder(context)
            .setTitle(msg)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setAdapter(adapter, null)
            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
              }
            }).show();
  }

    /*#######################################################
      ###               OVERRIDE METHODS                  ###
      #######################################################*/

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    setContentView(R.layout.main_layout);
//    setContentView(R.layout.testing_layout);
    init();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    new Handler().post(new Runnable() {
      @Override
      public void run() {
        refreshShowButton = findViewById(R.id.action_refresh_shows);
        refreshShowAnim = createRotateAnim(refreshShowButton, 360, 1000, true);
        runShowRefreshAnim();
      }
    });
    this.menu = menu;
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
//            case R.id.action_settings :
//                return true;

      case android.R.id.home:
        if (showsAdapter != null && showsAdapter.isEmpty()) {
          toast("Seznam je prázdný", Toast.LENGTH_SHORT);
          return true;
        }
        if (mDrawerLayout.isDrawerOpen(showsList)) {
          mDrawerLayout.closeDrawer(showsList);
        } else {
          mDrawerLayout.openDrawer(showsList);
        }
        return true;

      case R.id.action_refresh_shows:
        try {
          downloadShows();
        } catch (MalformedURLException e) {
          List list = new ArrayList<>();
          list.add(e.getLocalizedMessage());
          errorReportsDialog(list);
        }
        return true;

      case R.id.action_filter_all:
        onAllFilterClick();
        return true;

      case R.id.action_filter_audio:
        onAudioFilterClick();
        return true;

      case R.id.action_filter_video:
        onVideoFilterClick();
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

    /*#######################################################
      ###               PUBLIC METHODS                    ###
      #######################################################*/

  public void prepareMediaPlayerSource(String url) {
    if (mediaPlayer == null) {
      initMediaPlayer();
    }
    if (!isNetworkConnected()) {
      toast(ERROR_NO_CONNECTION, Toast.LENGTH_LONG);
      return;
    }

    PrepareStream ps = new PrepareStream(this, mediaPlayer);
    ps.setOnErrorListener(new PrepareStream.OnErrorListener() {
      @Override
      public void onError() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(LOADING)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(ERROR_ON_LOADING)
                .setPositiveButton(TRY_NEXT_RECORD, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    audioPlayerControl.next();
                    dialog.dismiss();
                  }
                }).setCancelable(true)
//                .setNegativeButton(STORNO, new DialogInterface.OnClickListener() {
//                  @Override
//                  public void onClick(DialogInterface dialogInterface, int i) {
//                    dialogInterface.dismiss();
//                  }
//                })
                .show();
      }
    });
    ps.execute(url);
  }

  /**
   * Checks network connection
   *
   * @return <code>true</code> if and only if device is connected,
   * <code>false</code> otherwise
   */
  public boolean isNetworkConnected() {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return ni != null;
  }

  public void hideLoading() {
    loadingBar.setVisibility(View.GONE);
  }

  public void showLoading() {
    loadingBar.setVisibility(View.VISIBLE);
    loadingBar.setAlpha(1f);
    if (recordsList != null && recordsList.getVisibility() != View.GONE) {
      recordsList.setVisibility(View.INVISIBLE);
    }
  }

  public void toast(String msg, int duration) {
    Toast.makeText(this, msg, duration).show();
  }

    /*#######################################################
      ###              PRIVATE METHODS                    ###
      #######################################################*/

  private RecordsAdapter createRecordsAdapter() {
    RecordsAdapter recordsAdapter = new RecordsAdapter(this);
    recordsAdapter.setOnRecordClickListener((record, index) -> {
      onRecordItemClick(record, index);
    });
    recordsAdapter.setOnMenuClickListener((item, source) -> {
      switch (item.getItemId()) {
        case R.id.context_action_detail:
          onRecordItemDetail(source.getActualRecord());
          return true;
//      case R.id.context_action_play:
//        onRecordItemClick();
//        return true;
        default:
          return false;
      }
    });
    recordsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onChanged() {
        showsAdapter.notifyDataSetChanged();
      }

      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        onChanged();
      }
    });

    return recordsAdapter;
  }

  private void crossfadeAnimation() {
    recordsList.setAlpha(0f);
    recordsList.setVisibility(View.VISIBLE);

    recordsList.animate()
            .alpha(1f)
            .setDuration(crossfadeAnimDuration)
            .setListener(null);

    loadingBar.animate()
            .alpha(0f)
            .setDuration(crossfadeAnimDuration)
            .setListener(new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                loadingBar.setVisibility(View.GONE);
              }
            });
  }

  private void downloadCoverImage(RecordItem record) {
    CoverImageDownloader downloader = (CoverImageDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.CoverImage);
    downloader.setOnCompleteListener(bitmap -> {
      audioController.setCoverImage(bitmap);
      record.setCover(bitmap);
    });
    downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
    downloader.execute(record.getRecord().getImgUrl());
  }

  private void downloadNextRecords(ShowItem item) {
    Log.d(getClass().getSimpleName(), "downloadNextRecords: for show " + item.getShow().info());
    RecordsDownloader downloader = (RecordsDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Records);
    downloader.setOnCompleteListener(result -> {
      onRecordsDownloaded(item, result);
      getRecordsListAdapter().update();
    });
    downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
    if (item.hasNextPageUrl()) {
      downloader.execute(item.getNextPageUrl());
      recordsAreDownloading = true;
    }
  }

  private void downloadShows() throws MalformedURLException {
    if (!isNetworkConnected()) {
      toast(ERROR_NO_CONNECTION, Toast.LENGTH_LONG);
    } else if (!showsAreDownloading) {
      showsAreDownloading = true;
      startDownloadShowsToast();
      runShowRefreshAnim();
      ShowsDownloader downloader = (ShowsDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Shows);
      downloader.setOnCompleteListener(set -> {
        showsAreDownloading = false;
        finishDownloadShowsToast();
        ArrayList<ShowItem> shows = new ArrayList(set.size());
        for (Show s : set) {
          shows.add(new ShowItem(s));
        }
        setShowsNavigation(shows.toArray(new ShowItem[shows.size()]));
        stopShowRefreshAnim();
        unlockNavigationDrawer();
      });
      downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
      downloader.execute(new URL(URL_E2 + SUB_URL_SHOWS));

    } else {
      toast(STILL_DOWNLOADING, Toast.LENGTH_SHORT);
    }
  }

  private void errorReportsDialog(List<String> reports) {
    errorReportsDialog(this, reports);
  }

  private void fillRecordsList(ShowItem show) {
    RecordsAdapter recordsAdapter = getRecordsListAdapter();
    audioController.setPlaylist(recordsAdapter);
    recordsAdapter.setSource(show);
    int selected = getSelectedRecordIndex();
    if (chosenRecord != null && chosenRecord.equals(recordsAdapter.getItem(selected))) {
      recordsAdapter.setSelected(selected);
    } else {
      recordsAdapter.setSelected(-1);
    }
    recordsList.scrollToPosition(getSelectedRecordIndex());
  }

  private void filterRecords(RecordType type) {
    RecordsAdapter recordsAdapter = getRecordsListAdapter();
    if (recordsAdapter == null) return;
    recordsAdapter.filter(type);
  }

  private void finishDownloadShowsToast() {
    if (!showsAreDownloading) {
      toast(SHOWS_ARE_READY, Toast.LENGTH_SHORT);
    }
  }

  private int getSelectedRecordIndex() {
    Integer selected = selectedRecords.get(playShow.getShow().getName());
    return selected == null ? -1 : selected.intValue();
  }

  /**
   * The last chosen {@link ShowItem} from navigation. Record items this show are actual in {@code recordsAdapter}.
   *
   * @return actual {@link ShowItem} or null
   */
  private ShowItem getChosenShow() {
    RecordsAdapter recordsAdapter = getRecordsListAdapter();
    if (recordsAdapter != null) {
      return recordsAdapter.getSource();
    }
    return null;
  }

  private RecordsAdapter getRecordsListAdapter() {
    return (RecordsAdapter) recordsList.getAdapter();
  }

  private void init() {
    try {
      downloadShows();
    } catch (MalformedURLException e) {
      List list = new ArrayList<>();
      list.add(e.getLocalizedMessage());
      errorReportsDialog(list);
    }
    loadingBar = findViewById(R.id.loadingPanel);
    loadingBar.setVisibility(View.GONE);
    // Retrieve and cache the system's default "short" animation time.
    crossfadeAnimDuration = getResources().getInteger(
            android.R.integer.config_shortAnimTime);

    initShowsList();
    initRecordsList();

    initActionBar();

    initMediaPlayer();
    initAudioController();
/*
    EditText textView = (EditText) findViewById(R.id.testingTextFied);
    textView.setText("https://m.static.lagardere.cz/evropa2/audio/2016/02/20160225-Meteorit.mp3");
    textView.setText("https:\\/\\/m.static.lagardere.cz\\/evropa2\\/audio\\/2016\\/06\\/20160617-\u010cesk\u00e1-volba-Ktery-druh-sportu-maji-cesi-nejradeji.mp3");
    textView.setText("https://m.static.lagardere.cz/evropa2/image/2016/01/Leos_Patrik-3-660x336.jpg");
    ((Button) findViewById(R.id.testingButton)).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //prepareMediaPlayerSource(textView.getText().toString());
        String url = textView.getText().toString();
        url = org.apache.commons.lang.StringEscapeUtils.unescapeJava(url);
        toast(url, Toast.LENGTH_LONG);
        //  MediaPlayer mp = MediaPlayer.create(getApplicationContext(), Uri.parse(url));
        //  mp.start();
        DownloaderFactory.CoverImageDownloader downloader = (DownloaderFactory.CoverImageDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.CoverImage);
        downloader.setOnCompleteListener(result -> {
          ImageView iv = (ImageView) findViewById(R.id.testingImage);
          iv.setImageBitmap((Bitmap) result);
          toast("cover was downloaded", Toast.LENGTH_LONG);
        });
        try {
          downloader.execute(new URL(url));
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
      }
    });
*/
  }

  private void initActionBar() {
    actionBar = getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeButtonEnabled(true);

    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
    mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
    mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

    final ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this,
            mDrawerLayout, R.string.navigation_drawer_open,
            R.string.navigation_drawer_close) {

      @Override
      public void onDrawerOpened(View drawerView) {
        super.onDrawerOpened(drawerView);
        actionBar.setTitle(R.string.navigation_title);
        actionBar.setSubtitle("");
      }

      @Override
      public void onDrawerClosed(View drawerView) {
        super.onDrawerClosed(drawerView);
        actionBar.setTitle(R.string.app_name);
        refreshActionBarSubtitle();
      }
    };

    mDrawerToggle.setDrawerIndicatorEnabled(true);
    //Set the ActionBarDrawerToggle in the layout
    mDrawerLayout.addDrawerListener(mDrawerToggle);

    //Hide the default Actionbar
    //getSupportActionBar().hide();
    // Call syncState() from your Activity's onPostCreate to synchronize the
    // indicator
    // with the state of the linked DrawerLayout after
    // onRestoreInstanceState has occurred
    mDrawerToggle.syncState();
  }

  private void initAudioController() {
    View controllerView = findViewById(R.id.audio_controller);
    audioPlayerControl = new AudioController.AudioPlayerControl() {
      @Override
      public void start() {
        mediaPlayer.start();
      }

      @Override
      public void stop() {
        if (mediaPlayer != null) {
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
        return mediaPlayer.getDuration() / 1000;
      }

      @Override
      public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition() / 1000;
      }

      @Override
      public void seekTo(int pos) {
        mediaPlayer.seekTo(pos * 1000);
      }

      @Override
      public boolean isPlaying() {
        if (mediaPlayer != null) {
          return mediaPlayer.isPlaying();
        }
        return false;
      }

      @Override
      void next() {
        RecordItem nextItem = audioController.getPlaylist().next();
        if (nextItem != null) {
          onRecordItemClick(nextItem, audioController.getPlaylist().indexOf(nextItem));
        }
      }

      @Override
      void previous() {
        if (getCurrentPosition() > 3) {
          seekTo(0);
        } else {
          RecordItem previousItem = audioController.getPlaylist().previous();
          if (previousItem == null) {
            seekTo(0);
          } else {
            onRecordItemClick(previousItem, audioController.getPlaylist().indexOf(previousItem));
          }
        }
      }

      @Override
      public boolean canPause() {
        return true;
      }

    };
    controllerView.setOnClickListener(v -> {
      if (audioController.isEnabled() && chosenRecord != null) {
        if (playShow.equals(getChosenShow())) {
          recordsList.smoothScrollToPosition(getSelectedRecordIndex());
        }
      }
    });
    audioController = new AudioController(controllerView, audioPlayerControl);
  }

  private void initMediaPlayer() {
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      /**
       * Called when the media file is ready for playback.
       *
       * @param mp the MediaPlayer that is ready for playback
       */
      @Override
      public void onPrepared(MediaPlayer mp) {
        audioController.setEnabled(true);
        audioController.setUpSeekBar();
                /* play mp3 */
        audioController.clickOnPlayPause();
      }

    });
    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        audioController.onCompletion();
      }
    });
  }

  private void initRecordsList() {
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
    recordsList = (RecyclerView) findViewById(R.id.recycler_view);

    RecordsAdapter recordsAdapter = createRecordsAdapter();
    recordsList.setAdapter(recordsAdapter);
    recordsList.setLayoutManager(linearLayoutManager);
    recordsList.addOnScrollListener(new EndlessScrollListener(() -> {
      if (!recordsAreDownloading && getChosenShow() != null) {
        downloadNextRecords(getChosenShow());
      }
    }, VISIBLE_TRESHOLD
    ));
    recordsList.addItemDecoration(new SpacesItemDecoration(ITEM_OFFSET));
//        recordsList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
    recordsList.setHasFixedSize(true);
    recordsList.setVisibility(View.GONE);
    swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
    swipeRefreshLayout.setOnRefreshListener(() -> onRefreshRecords());
    swipeRefreshLayout.setColorSchemeColors(Color.BLUE, Color.RED, Color.WHITE);
    swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.primary_material_dark);
    selectedRecords = new HashMap<>();
  }

  private void initShowsAdapter() {
    showsAdapter = new ShowsAdapter(this);
    showsAdapter.registerDataSetObserver(new DataSetObserver() {
      @Override
      public void onChanged() {
        if (!mDrawerLayout.isDrawerOpen(showsList)) {
          refreshActionBarSubtitle();
        }
      }
    });
  }

  private void initShowsList() {
    showsList = (ListView) findViewById(R.id.left_drawer);
    showsList.setSmoothScrollbarEnabled(true);
    initShowsAdapter();
    showsList.setAdapter(showsAdapter);
    showsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        ShowItem s = (ShowItem) showsAdapter.getItem(i);
        onNavigationItemClick(s, i);
      }
    });
  }

  private void lockNavigationDrawer(int lockMode) {
    mDrawerLayout.setDrawerLockMode(lockMode);
  }

  private void onAllFilterClick() {
    filterRecords(RecordType.All);
    updateFilterMenuItem(menu.findItem(R.id.action_filter_all));
  }

  private void onAudioFilterClick() {
    filterRecords(RecordType.Audio);
    updateFilterMenuItem(menu.findItem(R.id.action_filter_audio));
  }

  private void onVideoFilterClick() {
    filterRecords(RecordType.Video);
    updateFilterMenuItem(menu.findItem(R.id.action_filter_video));
  }

  private void onAudioItemClick(RecordItem record) {
    if (record.getRecord().hasMediaUrl()) {
      prepareMediaPlayerSource(record.getRecord().getMediaUrl().toString());
    } else {
      MediaUrlDownloader downloader = (MediaUrlDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.MediaUrl);
      downloader.setOnCompleteListener(url -> {
        if (url != null) {
          record.getRecord().setMediaUrl(url);
          prepareMediaPlayerSource(record.getRecord().getMediaUrl().toString());
        }
      });
      downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
      downloader.execute(new MediaUrlDownloader.Params(MediaUrlDownloader.Params.TYPE_AUDIO, record.getRecord().getWebSiteUrl()));
    }
  }

  private void onNavigationItemClick(ShowItem item, int position) {
    if (chosenShowPosition == position) {
      mDrawerLayout.closeDrawer(showsList);
      return;
    }

    showsAdapter.setSelectedItem(position);
    chosenShowPosition = position;
    //chosenShow = item;
    if (playShow == null) playShow = item;

    mDrawerLayout.closeDrawer(showsList);
    showLoading();

    if (item.isEmpty()) {
      DownloaderFactory.RecordsDownloader downloader = (DownloaderFactory.RecordsDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Records);
      downloader.setOnCompleteListener(result -> {
        onRecordsDownloaded(item, result);
        fillRecordsList(item);
        crossfadeAnimation();
      });
      downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
      downloader.execute(item.getShow().getWebSiteUrl());
    } else {
      fillRecordsList(item);
      crossfadeAnimation();
    }
  }

  private void onRecordItemClick(RecordItem record, int index) {
    toast(record.toString(), Toast.LENGTH_SHORT);
    int selected = getSelectedRecordIndex();
    if (selected == index) {
      audioController.clickOnPlayPause();
      return;
    }

    audioPlayerControl.stop();
    chosenRecord = record;
    playShow = getChosenShow();

    switch (record.getType()) {
      case Audio:
        onAudioItemClick(record);
        break;
      case Video:
        onVideoItemClick(record);
        break;
      default:
        break;
    }

    if (record.hasCover()) {
      audioController.setCoverImage(record.getCover());
    } else {
      audioController.resetCoverImage();
      downloadCoverImage(record);
    }

    audioController.setInfoLineText(record.getRecord().getName());
    RecordsAdapter recordsAdapter = getRecordsListAdapter();

    RecyclerView.ViewHolder viewHolder = recordsList.findViewHolderForAdapterPosition(index);
    if (viewHolder != null) {
      recordsAdapter.markViewHolder((RecordItemViewHolder) viewHolder);
    }
    viewHolder = recordsList.findViewHolderForAdapterPosition(selected);
    if (viewHolder != null) {
      recordsAdapter.unmarkViewHolder((RecordItemViewHolder) viewHolder);
    }

    selectedRecords.put(playShow.getShow().getName(), index);
    recordsAdapter.setSelected(index);
  }

  private void onRecordItemDetail(RecordItem recordItem) {
    AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Detail")
            .setMessage(recordItem.getRecord().info())
            .setIcon(android.R.drawable.ic_dialog_info)
            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
              }
            })
            .show();
  }

  private void onRecordsDownloaded(ShowItem item, Map<String, Object> result) {
    Log.d(getClass().getSimpleName(), "onRecordsDownloaded: for show " + item.getShow().info());
//    List<RecordItem> audioList = new ArrayList<>(),
//            videoList = new ArrayList<>();
//    Set<RecordItem> audioSet = new LinkedHashSet<>();
    Set<RecordItem> records = (Set<RecordItem>) result.get("records");
    if (!records.isEmpty()) {
      item.addRecordItems(records);
    }
    URL nextPage = (URL) result.get("nextPage");
    if (nextPage != null) item.setNextPageUrl(nextPage);
    recordsAreDownloading = false;
    Log.d(getClass().getSimpleName(), "onRecordsDownloaded: done");
  }

  private void onRefreshRecords() {
    DownloaderFactory.RecordsDownloader downloader = (DownloaderFactory.RecordsDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.Records);
    downloader.setOnCompleteListener(result -> {
      onRecordsDownloaded(playShow, result);
      getRecordsListAdapter().update();
      swipeRefreshLayout.setRefreshing(false);
      toast("Refresh done", Toast.LENGTH_LONG);
    });
    downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
    if (playShow.getShow().hasWebSiteUrl()) {
      Log.d(getClass().getSimpleName(), "onRefreshRecords: from " + playShow.getShow().getWebSiteUrl());
      downloader.execute(playShow.getShow().getWebSiteUrl());
      recordsAreDownloading = true;
    } else {
      swipeRefreshLayout.setRefreshing(false);
      toast("Refresh done", Toast.LENGTH_LONG);
    }
  }

  private void onVideoItemClick(RecordItem record) {
    if (record.getRecord().hasMediaUrl()) {
      playVideo(record.getRecord().getMediaUrl());
    } else {
      MediaUrlDownloader downloader = (MediaUrlDownloader) DownloaderFactory.getDownloader(DownloaderFactory.Type.MediaUrl);
      downloader.setOnCompleteListener(url -> {
        if (url != null) {
          record.getRecord().setMediaUrl(url);
          playVideo(record.getRecord().getMediaUrl());
        }
      });
      downloader.setOnErrorListener(errors -> errorReportsDialog(errors));
      downloader.execute(new MediaUrlDownloader.Params(MediaUrlDownloader.Params.TYPE_VIDEO, record.getRecord().getWebSiteUrl()));
    }
  }

  private void playVideo(URL url) {
   /* if (recordItem.getType().compareTo(Type.Video) != 0) return;
    if (audioController.isPlaying()) audioController.clickOnPlayPause();
*/
    Uri path = Uri.parse(url.toExternalForm());
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(path, "video/*");
//    intent.setType("text/plain");

// Verify that the intent will resolve to an activity
    if (intent.resolveActivity(getPackageManager()) != null) {
      startActivity(intent);
    } else {
      toast("None app to playing video", Toast.LENGTH_LONG);
    }
  }

  private void refreshActionBarSubtitle() {
    if (getChosenShow() != null) {
      actionBar.setSubtitle(getChosenShow().getShow().getName() + " (" + getRecordsListAdapter().getItemCount() + ")");
    }
  }

  private void runShowRefreshAnim() {
    if (refreshShowButton != null && refreshShowAnim != null) {
      if (showsAreDownloading) {
        refreshShowButton.startAnimation(refreshShowAnim);
      }
    }
  }

  private void startDownloadShowsToast() {
    if (!showsAreDownloading) toast(DOWNLOADING_SHOWS, Toast.LENGTH_SHORT);
  }

  private void stopShowRefreshAnim() {
    if (refreshShowButton != null) {
      if (!showsAreDownloading) {
        refreshShowButton.clearAnimation();
      }
    }
  }

  private void setShowsNavigation(ShowItem[] shows) {
    if (showsAdapter == null) {
      initShowsAdapter();
      showsList.setAdapter(showsAdapter);
    }
    showsAdapter.setShows(shows);
    showsAdapter.notifyDataSetChanged();
  }

  private void unlockNavigationDrawer() {
    mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
  }

  private void updateFilterMenuItem(MenuItem item) {
    MenuItem filterItem = menu.findItem(R.id.filter_records_list);
    filterItem.setTitle(item.getTitle());
    filterItem.setIcon(item.getIcon());
  }
}