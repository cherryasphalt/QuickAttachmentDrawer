package hu.calvin.quickmediadrawer;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;


public class MainActivity extends ActionBarActivity implements QuickMediaDrawer.QuickMediaDrawerListener {
    QuickMediaDrawer quickMediaDrawer;
    ImageView imageView;

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
    public void onPanelCollapsed() {

    }

    @Override
    public void onPanelExpanded() {

    }

    @Override
    public void onPanelHalfExpanded() {

    }

    @Override
    public void onImageCapture(Uri imageUri) {
        quickMediaDrawer.setDrawerState(QuickMediaDrawer.DrawerState.COLLAPSED);
        ContentResolver cr = getContentResolver();
        try {
            InputStream in = cr.openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize=8;
            Bitmap thumbnail = BitmapFactory.decodeStream(in,null,options);

            ExifInterface exif = new ExifInterface(imageUri.getPath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    matrix.postRotate(0);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(90);
                    break;
            }
            thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
            imageView.setImageBitmap(thumbnail);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
