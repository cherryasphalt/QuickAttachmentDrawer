package hu.calvin.quickmediadrawer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import hu.calvin.quickattachmentdrawer.QuickAttachmentDrawer;
import hu.calvin.quickattachmentdrawer.QuickCamera;

public class MainActivity extends ActionBarActivity implements QuickAttachmentDrawer.QuickAttachmentDrawerListener,
                                                                QuickCamera.QuickCameraListener{
    QuickAttachmentDrawer quickAttachmentDrawer;
    ImageView imageView;
    ActionBar actionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.sample_imageview);
        quickAttachmentDrawer = (QuickAttachmentDrawer) findViewById(R.id.quick_media_drawer);
        quickAttachmentDrawer.setQuickAttachmentDrawerListener(this);
        quickAttachmentDrawer.setQuickCameraListener(this);
        findViewById(R.id.quick_media_expand).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickAttachmentDrawer.setDrawerStateAndAnimate(QuickAttachmentDrawer.HALF_EXPANDED);
            }
        });
        findViewById(R.id.quick_media_collapse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickAttachmentDrawer.setDrawerStateAndAnimate(QuickAttachmentDrawer.COLLAPSED);
            }
        });
        findViewById(R.id.quick_media_full_expand).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickAttachmentDrawer.setDrawerStateAndAnimate(QuickAttachmentDrawer.FULL_EXPANDED);
            }
        });
        actionBar = getSupportActionBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        quickAttachmentDrawer.onStop();
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        quickAttachmentDrawer.onStart();
    }

    @Override
    public void onCollapsed() {
        if (actionBar != null) actionBar.show();
    }

    @Override
    public void onExpanded() {
        if (actionBar != null) actionBar.hide();
    }

    @Override
    public void onHalfExpanded() {
        if (actionBar != null) actionBar.hide();
    }

    @Override
    public void displayCameraUnavailableError() {
        Toast.makeText(MainActivity.this, R.string.quick_camera_unavailable, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onImageCapture(final byte[] data) {
        quickAttachmentDrawer.setDrawerStateAndAnimate(QuickAttachmentDrawer.COLLAPSED);
        ViewCompat.postOnAnimation(quickAttachmentDrawer, new Runnable() {
            public void run() {
                Bitmap thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length);
                imageView.setImageBitmap(thumbnail);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (quickAttachmentDrawer.getDrawerState() != QuickAttachmentDrawer.COLLAPSED)
            quickAttachmentDrawer.setDrawerStateAndAnimate(QuickAttachmentDrawer.COLLAPSED);
        else
            super.onBackPressed();
    }
}
