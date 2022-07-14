package mwe.downloadmanageroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DefaultDatabaseProvider;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.WritableDownloadIndex;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheSpan;
import com.google.android.exoplayer2.upstream.cache.ContentMetadata;
import com.google.android.exoplayer2.upstream.cache.ContentMetadataMutations;
import com.google.android.exoplayer2.upstream.cache.DefaultContentMetadata;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements View.OnClickListener, DownloadManager.Listener {

  private static final int DOWNLOADS_TO_ADD = 2_000;
  private TextView button;

  @SuppressLint("SetTextI18n")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    button = new Button(this);
    button.setBackgroundColor(Color.BLACK);
    button.setTextColor(Color.WHITE);
    button.setTypeface(null, Typeface.BOLD);
    button.setText("CLICK HERE TO BEGIN");
    button.setOnClickListener(this);
    setContentView(button);
  }

  @Override
  public void onClick(View v) {
    button.setEnabled(false);
    DefaultDownloadIndex writableDownloadIndex = new DefaultDownloadIndex(
        new DefaultDatabaseProvider(
            new SQLiteOpenHelper(this, "SQLiteOpenHelper impl", null, 1) {
              @Override
              public void onCreate(SQLiteDatabase db) {
              }

              @Override
              public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
              }
            }
        )
    );
    for (int i = 0; i < DOWNLOADS_TO_ADD; i++) {
      try {
        writableDownloadIndex.putDownload(
            new Download(
            new DownloadRequest.Builder(
                String.valueOf(i),
                Uri.EMPTY
            ).build(),
                Download.STATE_REMOVING,
                0,
                1,
                2,
                Download.STOP_REASON_NONE,
                Download.FAILURE_REASON_NONE
            )
        );
      } catch (DatabaseIOException e) {
        throw new RuntimeException(e);
      }
    }
    button.setText("COMPLETED INDEX WRITES");
    button.setBackgroundColor(Color.RED);
    try {
      Field internalHandlerField = DownloadManager.class.getDeclaredField("internalHandler");
      internalHandlerField.setAccessible(true);
      Object internalHandler = internalHandlerField.get(new DownloadManager(
          this,
          writableDownloadIndex,
          new DefaultDownloaderFactory(
              new CacheDataSource.Factory()
                  .setUpstreamDataSourceFactory(new DefaultDataSource.Factory(this))
                  .setCache(new SimpleCache(this.getExternalCacheDir(), new NoOpCacheEvictor())),
              Executors.newSingleThreadExecutor()
          )
      ));
      Field activeTasksField = Class.forName("com.google.android.exoplayer2.offline.DownloadManager$InternalHandler").getDeclaredField("activeTasks");
      activeTasksField.setAccessible(true);
      final Map activeTasks = (Map) activeTasksField.get(internalHandler);
      new Thread(() -> {
        while (activeTasks.isEmpty());
        while (true) {
          if (activeTasks.isEmpty()) {
            MainActivity.this.runOnUiThread(() -> {
              button.setText("COMPLETED INITIALIZATION SYNC");
              button.setBackgroundColor(Color.BLUE);
            });
            break;
          }
          try {
            Thread.sleep(1_000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }).start();
    } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
