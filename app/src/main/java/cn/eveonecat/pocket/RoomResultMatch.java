package cn.eveonecat.pocket;
import java.util.regex.*;

final class RoomResultMatch {
    private static final Pattern LABEL=Pattern.compile("房间号\\s*[:：]?\\s*([1-9][0-9]{0,14})(?![0-9])");
    static boolean matches(String text,String room){
        if(text==null||text.length()>200||room==null||!room.matches("[1-9][0-9]{0,14}"))return false;
        Matcher m=LABEL.matcher(text);if(!m.find())return false;
        return m.group(1).equals(room)&&!m.find();
    }
}
