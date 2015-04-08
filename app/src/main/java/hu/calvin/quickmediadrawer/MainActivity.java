package hu.calvin.quickmediadrawer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;


public class MainActivity extends ActionBarActivity implements QuickMediaDrawer.QuickMediaDrawerListener {
    QuickMediaDrawer quickMediaDrawer;
    ImageView imageView;
    ActionBar actionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.sample_imageview);
        quickMediaDrawer = (QuickMediaDrawer) findViewById(R.id.quick_media_drawer);
        quickMediaDrawer.setQuickMediaDrawerListener(this);
        findViewById(R.id.quick_media_expand).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.HALF_EXPANDED);
            }
        });
        findViewById(R.id.quick_media_collapse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.COLLAPSED);
            }
        });
        findViewById(R.id.quick_media_full_expand).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.FULL_EXPANDED);
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
    protected void onPause() {
        super.onPause();
        quickMediaDrawer.onPause();
    }

    @Override
    protected void onResume() {
        super.onPause();
        quickMediaDrawer.onResume();
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
    public void onImageCapture(String imageFilename, int rotation) {
        quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.COLLAPSED);
        try {
            InputStream in = openFileInput(imageFilename);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize=1;
            Bitmap thumbnail = BitmapFactory.decodeStream(in, null, options);
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
            imageView.setImageBitmap(thumbnail);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (quickMediaDrawer.getDrawerState() != QuickMediaDrawer.DrawerState.COLLAPSED)
            quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.COLLAPSED);
        else
            super.onBackPressed();
    }
}
