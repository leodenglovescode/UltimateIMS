package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 经官方资料审阅的数据 APN；MCC/MNC 只用于新 APN 行匹配，不改变 SIM 标识。 */
public record ApnProfile(String id, String apn, String types, String protocol, String roamingProtocol, String mmsc) {
    public static ApnProfile reviewed(String id, String verifiedOperator) {
        if (id.equals("CMNET") && Set.of("46000","46002","46007","46008").contains(verifiedOperator)) return new ApnProfile(id,"cmnet","default,supl","IP","IP","");
        if (id.equals("TMOBILE") && verifiedOperator.equals("310260")) return new ApnProfile(id,"fast.t-mobile.com","default,supl,mms","IPV4V6","IPV4V6","http://mms.msg.eng.t-mobile.com/mms/wapenc");
        throw new IllegalArgumentException("Reviewed profile does not match verified SIM operator");
    }
    public static int ownedSetId(String tag) {
        ownedName(tag); int id = (int)(java.util.UUID.fromString(tag).getLeastSignificantBits() & 0x7fffffffL); return id == 0 ? 1 : id;
    }
    public static String ownedName(String tag) {
        if (tag == null || !tag.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid APN operation identity");
        return "UltimateIMS " + tag;
    }
}
