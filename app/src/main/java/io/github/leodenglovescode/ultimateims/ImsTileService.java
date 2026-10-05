package io.github.leodenglovescode.ultimateims;

import android.service.quicksettings.*;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

/** 图块只显示当前已读取 IMS 注册并打开恢复页，不自动获取权限或切换配置。 */
public final class ImsTileService extends TileService {
    @Override public void onStartListening() {
        var workspace = ((UltimateApplication)getApplication()).workspace(); var tile = getQsTile();
        if (tile == null) return;
        boolean known = false, registered = false;
        for (var sim : workspace.sims()) if (sim.containsKey("imsRegistered")) { known = true; registered |= sim.getBoolean("imsRegistered"); }
        tile.setLabel(getString(R.string.ims_tile)); tile.setSubtitle(getString(!known ? R.string.unknown : registered ? R.string.registered : R.string.not_registered));
        tile.setState(known && registered ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); tile.updateTile();
    }
    // API 33 尚无 PendingIntent 重载；仅在该版本使用旧接口。
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Override public void onClick() {
        Intent target = new Intent(this,MainActivity.class).putExtra("page",4).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this,42,target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        else startActivityAndCollapse(target);
    }
}
