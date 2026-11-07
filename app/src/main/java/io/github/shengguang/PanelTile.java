package io.github.shengguang;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 快捷设置磁贴：点一下打开面板。 */
public class PanelTile extends TileService {
    @Override
    public void onStartListening() {
        CrashLog.install(this);
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel("声光");
        t.setSubtitle("音量 " + Sound.get(this).level());
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
    }
}
