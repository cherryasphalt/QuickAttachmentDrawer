package hu.calvin.quickmediadrawer;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final QuickMediaDrawer quickMediaDrawer = (QuickMediaDrawer) findViewById(R.id.quick_media_drawer);
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
}
