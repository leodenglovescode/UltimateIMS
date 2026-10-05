package io.github.leodenglovescode.ultimateims.backend;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import io.github.leodenglovescode.ultimateims.core.ApnProfile;
import java.util.*;

/** 固定 provider 路径与官方审阅字段；不修改已有 APN 行，不提供任意 ContentProvider 操作。 */
final class ApnOperations {
    private final Context context;
    private final ContentResolver resolver;
    private static final Uri ROWS = Uri.parse("content://telephony/carriers");
    private static final String[] COLUMNS = {"_id","name","apn","numeric","type","protocol","roaming_protocol","mmsc","apn_set_id"};
    ApnOperations(Context context) { this.context = context; resolver = context.getContentResolver(); }
    private Uri preferred(int sub) { return Uri.parse("content://telephony/carriers/preferapn/subId/"+sub); }
    private Bundle one(Uri uri, String selection, String[] args) {
        try (Cursor cursor = resolver.query(uri,COLUMNS,selection,args,null)) {
            if (cursor == null) throw new IllegalStateException("APN query unreadable");
            Bundle result = new Bundle();
            if (cursor.moveToFirst()) for (String column : COLUMNS) {
                int index = cursor.getColumnIndexOrThrow(column);
                if (column.equals("_id")) result.putLong("id",cursor.getLong(index));
                else result.putString(column,cursor.isNull(index) ? null : cursor.getString(index));
            }
            if (cursor.moveToNext()) throw new IllegalStateException("Ambiguous APN row");
            return result;
        }
    }
    private String operator(int sub) {
        String numeric = context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub).getSimOperator();
        if (!numeric.matches("[0-9]{5,6}")) throw new IllegalStateException("SIM operator unknown"); return numeric;
    }
    Bundle execute(int op, int sub, Bundle args) {
        String numeric = operator(sub);
        if (op == Protocol.APN_READ) { Bundle row = one(preferred(sub),null,null); row.putString("operator",numeric); return row; }
        String name = ApnProfile.ownedName(args.getString("tag"));
        Bundle own = one(ROWS,"name=? AND numeric=?",new String[]{name,numeric});
        if (op == Protocol.APN_APPLY) {
            ApnProfile profile = ApnProfile.reviewed(args.getString("profile",""),numeric);
            if (!own.containsKey("id")) {
                int setId = ApnProfile.ownedSetId(args.getString("tag"));
                // AOSP 会合并唯一键冲突，name 不在唯一键中；独立 APN 分组避免改写已有行。
                try (Cursor existing = resolver.query(ROWS,new String[]{"_id"},"apn_set_id=?",new String[]{Integer.toString(setId)},null)) {
                    if (existing == null || existing.moveToFirst()) throw new IllegalStateException("APN operation group occupied or unreadable");
                }
                ContentValues values = new ContentValues(); values.put("apn_set_id",setId); values.put("name",name); values.put("apn",profile.apn());
                values.put("numeric",numeric); values.put("mcc",numeric.substring(0,3)); values.put("mnc",numeric.substring(3));
                values.put("type",profile.types()); values.put("protocol",profile.protocol()); values.put("roaming_protocol",profile.roamingProtocol());
                values.put("mmsc",profile.mmsc()); values.put("carrier_enabled",1);
                Uri inserted = resolver.insert(ROWS,values); if (inserted == null) throw new IllegalStateException("APN insert rejected");
                own = one(ROWS,"name=? AND numeric=?",new String[]{name,numeric});
            }
            if (!Objects.equals(own.getString("apn_set_id"),Integer.toString(ApnProfile.ownedSetId(args.getString("tag")))) || !Objects.equals(own.getString("apn"),profile.apn()) || !Objects.equals(own.getString("type"),profile.types())
                    || !Objects.equals(own.getString("protocol"),profile.protocol()) || !Objects.equals(own.getString("roaming_protocol"),profile.roamingProtocol())
                    || !Objects.equals(own.getString("mmsc",""),profile.mmsc())) throw new IllegalStateException("Owned APN verification failed");
            select(sub,own.getLong("id")); return own;
        }
        if (op != Protocol.APN_RESTORE) throw new SecurityException("Unknown APN action");
        Bundle original = args.getBundle("original");
        if (original == null || !original.containsKey("id")) throw new IllegalStateException("Verified original preferred APN unavailable");
        Bundle currentOriginal = one(ROWS,"_id=? AND numeric=?",new String[]{Long.toString(original.getLong("id")),numeric});
        for (String column : COLUMNS) if (!column.equals("_id") && !Objects.equals(currentOriginal.getString(column),original.getString(column))) throw new IllegalStateException("Original APN changed; restoration refused");
        select(sub,original.getLong("id"));
        // 仅删除本次日志的自有行；原始和其他 APN 绝不删除。
        if (own.containsKey("id")) resolver.delete(ROWS,"_id=? AND name=? AND numeric=?",new String[]{Long.toString(own.getLong("id")),name,numeric});
        if (one(ROWS,"name=? AND numeric=?",new String[]{name,numeric}).containsKey("id")) throw new IllegalStateException("Owned APN removal unverified");
        return one(preferred(sub),null,null);
    }
    private void select(int sub, long id) {
        ContentValues target = new ContentValues(); target.put("apn_id",id); resolver.update(preferred(sub),target,null,null);
        if (one(preferred(sub),null,null).getLong("id",-1) != id) throw new IllegalStateException("Preferred APN write unverified");
    }
}
