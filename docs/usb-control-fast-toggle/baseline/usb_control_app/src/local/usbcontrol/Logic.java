package local.usbcontrol;

import java.util.regex.*;

/** Pure calculations and conservative ROM capability parsing; JVM-testable. */
public final class Logic {
    private Logic() {}
    public static int lastNumber(String text, String label, int fallback) {
        Matcher m = Pattern.compile(label + ":\\s*(\\d+)").matcher(text);
        int value = fallback;
        while (m.find()) value = Integer.parseInt(m.group(1));
        return value;
    }
    public static int[] size(String text) {
        Matcher m = Pattern.compile("(?:Physical|Override) size:\\s*(\\d+)x(\\d+)").matcher(text);
        int[] v = {0, 0};
        while(m.find()) { v[0] = Integer.parseInt(m.group(1)); v[1] = Integer.parseInt(m.group(2)); }
        return v;
    }
    public static int densityForWidth(int widthPx, int smallestDp) {
        if (widthPx < 1 || smallestDp < 280 || smallestDp > 720) throw new IllegalArgumentException("最小宽度范围为 280–720 dp");
        int dpi = Math.round(widthPx * 160f / smallestDp);
        if(dpi < 120 || dpi > 1000) throw new IllegalArgumentException("超出此屏幕的密度范围");
        return dpi;
    }
    public static String installNamespace(String globals, String secure, String system) {
        // Only this explicit USB-install key is supported; never invent absent settings
        // or change ADB verifier / unknown-source / account-security settings.
        String[] spaces = {"global", "secure", "system"};
        String[] lists = {globals, secure, system};
        String match = null;
        for(int i=0;i<lists.length;i++) {
            if(Pattern.compile("(?m)^adb_install_enabled=[01]\\r?$").matcher(lists[i]).find()) {
                if(match != null) return null; // ambiguous vendor implementation
                match = spaces[i];
            }
        }
        return match;
    }
    public static float clamp(float v, float min, float max) { return Float.isNaN(v)?min:Math.max(min, Math.min(max, v)); }
    public static String miuiInstallState(String probe) {
        // GET's default=false is not evidence of a supported key: require CONTAIN_KEY
        // and the matching system confirmation component as well as a unique value.
        if(!Pattern.compile("(?m)^Result: Bundle\\[\\{CONTAIN_KEY=true\\}\\]\\r?$").matcher(probe).find()
            ||!Pattern.compile("(?m)^com\\.miui\\.securitycenter/com\\.miui\\.permcenter\\.install\\.AdbInstallVerifyActivity\\r?$").matcher(probe).find())return null;
        Matcher m=Pattern.compile("(?m)^Result: Bundle\\[\\{security_adb_install_enable=(true|false)\\}\\]\\r?$").matcher(probe);
        if(!m.find())return null;String value="true".equals(m.group(1))?"1":"0";
        return m.find()?null:value;
    }
    public static float coverScale(int bw,int bh,int vw,int vh,float zoom) {
        return Math.max(vw/(float)bw, vh/(float)bh) * clamp(zoom, 1f, 4f);
    }
}
