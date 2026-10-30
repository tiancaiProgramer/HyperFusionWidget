package dev.fusion.widget;

import java.util.*;

public final class PresentationTest {
    private static int checks;
    static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    static List<String> list(String... ids) {return Arrays.asList(ids);}
    public static void main(String[] args) {
        List<String> ids=list("phone","camera","rice","speaker","tv","oven","watch");
        DevicePresentation model=DevicePresentation.migrate(ids,Collections.emptySet(),2);
        check(model.home(ids).equals(list("phone","camera")),"upgrade preserves the previous visible homepage");
        for(String id:list("rice","speaker","tv","oven"))model.pin(id,true);
        check(model.home(ids).size()==6,"six selected devices are all resident immediately");
        for(String id:list("camera","rice","oven"))model.pin(id,false);
        check(model.home(ids).equals(list("phone","speaker","tv")),"arbitrary three-device homepage has no fixed count cap");
        check(model.all(ids).containsAll(ids)&&model.all(ids).contains("phone"),"all devices includes homepage devices and unpinned devices");
        model.hide("tv",true);
        check(!model.home(ids).contains("tv")&&!model.all(ids).contains("tv")&&model.concealed(ids).equals(list("tv")),"hide removes direct tiles and collects them in the hidden section");
        model.pin(DevicePresentation.HIDDEN,true);
        check(model.home(ids).equals(list("phone","speaker",DevicePresentation.HIDDEN)),"hidden section can be pinned alongside devices");
        model.pin("tv",true);
        check(!model.home(ids).contains("tv"),"hidden device cannot also appear directly on homepage");
        model.move(DevicePresentation.HOME,DevicePresentation.HIDDEN,"phone");
        check(model.home(ids).equals(list(DevicePresentation.HIDDEN,"phone","speaker"))&&model.all(ids).get(0).equals("phone"),"homepage order is independent of full-list order");
        model.move(DevicePresentation.ALL,"watch","phone");
        check(model.all(ids).get(0).equals("watch")&&model.home(ids).get(0).equals(DevicePresentation.HIDDEN),"full-list order is independent of homepage order");
        model.hide("rice",true);model.move(DevicePresentation.CONCEALED,"rice","tv");
        check(model.concealed(ids).equals(list("rice","tv")),"hidden devices have their own order");
        model.hide("tv",false);
        check(model.all(ids).contains("tv")&&!model.home(ids).contains("tv"),"unhide restores full-list availability without silently pinning");
        List<String> after=new ArrayList<>(ids);after.add("new-lock");List<String> homeBefore=model.home(ids);model.discover(after);
        check(model.all(after).get(model.all(after).size()-1).equals("new-lock")&&model.home(after).equals(homeBefore),"new devices append to full list without changing homepage selection");
        List<String> disconnected=list("camera","rice","tv","oven","watch","new-lock");
        check(!model.home(disconnected).contains("phone")&&model.home(after).equals(homeBefore),"temporary disappearance does not erase saved pins or order");
        DevicePresentation migrated=DevicePresentation.migrate(ids,new HashSet<>(list("camera")),2);
        check(migrated.home(ids).equals(list("phone","rice"))&&migrated.hidden.isEmpty()&&migrated.all(ids).contains("camera"),"old unchecked devices migrate as unpinned, not explicitly hidden");
        DevicePresentation empty=new DevicePresentation();empty.discover(list("phone","phone","",null));empty.pin("phone",true);empty.pin("phone",true);
        check(empty.home(list("phone","phone")).equals(list("phone"))&&empty.all(list("phone")).size()==2,"duplicates and missing IDs do not create duplicate modules");
        empty.pin("phone",false);
        check(empty.home(list("phone")).isEmpty()&&empty.all(list("phone")).contains("phone"),"empty homepage is intentional and full list remains available");
        model.hide(DevicePresentation.HIDDEN,true);
        check(!model.hidden.contains(DevicePresentation.HIDDEN)&&!model.move("invalid","phone","tv"),"special module and invalid ordering requests are constrained");
        System.out.println(checks+" device-presentation behavior checks passed");
    }
}
