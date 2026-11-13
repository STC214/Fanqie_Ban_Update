package local.usbcontrol;

import java.util.*;
import java.io.IOException;

public final class SystemAppsTest {
    static final String[] CARRIER_PACKAGES={
        "com.mobiletools.systemhelper","com.oppo.ctautoregist","com.coloros.regservice",
        "com.xiaomi.registration","com.miui.dmregservice","com.vivo.devicereg",
        "com.huawei.android.UEInfoCheck","com.huawei.android.AutoRegSms","com.huawei.regservice",
        "com.hihonor.android.UEInfoCheck","com.hihonor.android.AutoRegSms","com.hihonor.regservice",
        "com.meizu.regservice"
    };
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static final class Fake implements SystemApps.Runner {
        final Set<String> system=new LinkedHashSet<>(Arrays.asList("com.miui.hybrid","com.miui.newhome","com.android.browser","com.miui.systemAdSolution","com.miui.contentextension"));
        final Set<String> installed=new LinkedHashSet<>(system);
        final Set<String> data=new LinkedHashSet<>(system);
        final List<String> calls=new ArrayList<>();
        String fail="",lie="";
        public String run(String cmd)throws Exception {
            calls.add(cmd);
            if(cmd.startsWith("pm list packages")){
                Set<String> pkgs=cmd.contains(" -s -u ")?system:installed;
                String filter=cmd.substring(cmd.lastIndexOf(' ')+1);boolean filtered=filter.contains(".");
                StringBuilder s=new StringBuilder();for(String pkg:pkgs)if(!filtered||pkg.contains(filter))s.append("package:").append(pkg).append('\n');return s.toString();
            }
            String pkg=cmd.substring(cmd.lastIndexOf(' ')+1);
            if(pkg.equals(fail))throw new IOException("fixture failure");
            boolean restore=cmd.startsWith("cmd package install-existing");
            if(!pkg.equals(lie)){if(restore)installed.add(pkg);else {installed.remove(pkg);if(!cmd.contains(" -k "))data.remove(pkg);}}
            return "Success";
        }
    }
    public static void main(String[] args)throws Exception {
        Fake f=new Fake();List<SystemApps.Entry> entries=SystemApps.scan(f,10);
        check(entries.size()==4,"only allowlisted system packages; no contentextension");
        check(f.calls.get(0).equals("pm list packages -s -u --user 10"),"system membership includes removed packages for selected user");
        check(SystemApps.command("com.miui.hybrid",10,false).equals("pm uninstall --user 10 com.miui.hybrid"),"user-scoped uninstall without keep-data flag");
        check(SystemApps.command("com.miui.hybrid",10,true).equals("cmd package install-existing --user 10 com.miui.hybrid"),"restore command");
        String result=SystemApps.apply(f,10,Arrays.asList(entries.get(0)),false);
        check(result.contains("已卸载")&&!f.installed.contains("com.miui.hybrid"),"selected package removed");
        check(!f.data.contains("com.miui.hybrid"),"selected app data deleted");
        check(f.data.contains("com.android.browser"),"unselected app data intact");
        check(f.installed.contains("com.android.browser"),"unselected package intact");
        check(!SystemApps.scan(f,10).get(0).installed,"removed system package remains restorable");
        check(SystemApps.apply(f,10,Arrays.asList(entries.get(0)),true).contains("已恢复"),"restore verification");
        check(f.installed.contains("com.miui.hybrid"),"roundtrip restored");
        check(!f.data.contains("com.miui.hybrid"),"restore does not resurrect deleted data");
        check(SystemApps.apply(f,10,Arrays.asList(entries.get(0)),true).contains("跳过"),"stale restore skipped");
        f.fail="com.miui.hybrid";
        result=SystemApps.apply(f,10,Arrays.asList(entries.get(0),entries.get(2)),false);
        check(result.contains("fixture failure")&&result.contains("浏览器：已卸载"),"partial failure continues per item");
        f.fail="";f.lie="com.miui.newhome";
        check(SystemApps.apply(f,10,Arrays.asList(entries.get(1)),false).contains("读回未符合预期"),"never trust success text without readback");
        int before=f.calls.size();
        check(SystemApps.apply(f,10,Arrays.asList(new SystemApps.Entry("unknown","evil.pkg; reboot",true)),false).contains("跳过"),"forged selection rejected after rescan");
        check(f.calls.size()==before+2,"forged selection causes no mutation");
        f.system.remove("com.miui.hybrid");
        check(SystemApps.apply(f,10,Arrays.asList(entries.get(0)),false).contains("未识别"),"system membership revalidated before mutation");
        Fake dedup=new Fake();SystemApps.apply(dedup,0,Arrays.asList(entries.get(0),entries.get(0)),false);
        check(Collections.frequency(dedup.calls,"pm uninstall --user 0 com.miui.hybrid")==1,"duplicate selection only runs once");
        boolean rejected=false;try{SystemApps.command("com.miui.hybrid; reboot",0,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unknown/injected package rejected");
        rejected=false;try{SystemApps.command("com.miui.hybrid",-1,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"all-users selector rejected");
        rejected=false;try{SystemApps.apply(f,0,new ArrayList<>(),false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"empty selection rejected");
        rejected=false;try{SystemApps.scan(c->"Failure [fixture]",0);}catch(IOException e){rejected=true;}check(rejected,"malformed inventory fails closed");
        Fake nonSystem=new Fake();nonSystem.system.clear();check(SystemApps.scan(nonSystem,0).isEmpty(),"matching third-party package never eligible");
        for(int user:new int[]{0,999}){
            Fake carrier=new Fake();
            carrier.system.addAll(Arrays.asList(CARRIER_PACKAGES));
            carrier.installed.addAll(carrier.system);carrier.data.addAll(carrier.system);
            List<SystemApps.Entry> all=SystemApps.scan(carrier,user);
            check(all.size()==17,"original and all 13 carrier packages detected, no unrelated package");
            check(new HashSet<>(Arrays.asList(CARRIER_PACKAGES)).size()==13,"13 unique carrier identities");
            for(String pkg:CARRIER_PACKAGES){
                SystemApps.Entry entry=null;for(SystemApps.Entry e:all)if(e.pkg.equals(pkg))entry=e;
                check(entry!=null&&entry.installed,"carrier package exact match "+pkg);
                check(SystemApps.command(pkg,user,false).equals("pm uninstall --user "+user+" "+pkg),"carrier uninstall scoped without keep-data "+pkg);
                check(SystemApps.command(pkg,user,true).equals("cmd package install-existing --user "+user+" "+pkg),"carrier restore scoped "+pkg);
                SystemApps.apply(carrier,user,Arrays.asList(entry),false);
                check(!carrier.installed.contains(pkg)&&!carrier.data.contains(pkg),"carrier uninstall deletes data "+pkg);
                SystemApps.apply(carrier,user,Arrays.asList(entry),true);
                check(carrier.installed.contains(pkg)&&!carrier.data.contains(pkg),"carrier restore excludes deleted data "+pkg);
                check(carrier.installed.contains("com.miui.contentextension"),"unrelated app untouched "+pkg);
                carrier.system.remove(pkg);
                int start=carrier.calls.size();SystemApps.apply(carrier,user,Arrays.asList(entry),false);
                check(carrier.calls.size()==start+2&&carrier.installed.contains(pkg),"carrier third-party identity excluded after rescan "+pkg);
            }
        }
        System.out.println("PASS: "+checks+" system-app assertions; uninstall/restore roundtrip, isolation, partial failures, readback and validation");
    }
}
